package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.collaboration.application.services.CollaborationService;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.infrastructure.events.CollaborationOutbox;
import com.collabtech.platform.collaboration.interfaces.acl.CollaborationEventConsumer;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("test")
class CollaborationApiTests {
    @Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@LocalServerPort int port;
    @Autowired CollaborationService service;@Autowired Participants participants;@Autowired SelectedTerms source;@Autowired UnitOfWork work;
    @Autowired AgreementRepository agreements;@Autowired CollaborationRepository collaborations;@Autowired Reads reads;@Autowired CollaborationOutbox events;
    final HttpClient http=HttpClient.newHttpClient();
    record Result(int status,JsonNode body){}record User(String token,String account,String profile){}
    record Fixture(User brand,User creator,String campaign,String application){}
    Result call(String method,String path,String token,Object body)throws Exception{
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(30));
        if(token!=null)req.header("Authorization","Bearer "+token);if(body!=null)req.header("Content-Type","application/json");
        req.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=http.send(req.build(),HttpResponse.BodyHandlers.ofString());return new Result(response.statusCode(),json.readTree(response.body()));
    }
    User user(String type)throws Exception{String email="agreement-"+UUID.randomUUID()+"@example.com";String password="AgreementTest123!";
        assertEquals(201,call("POST","/auth/"+type,null,Map.of(type.equals("brands")?"businessName":"displayName","Nombre","email",email,"password",password)).status);
        var session=call("POST","/auth/sessions",null,Map.of("email",email,"password",password));assertEquals(200,session.status);
        return new User(session.body.path("accessToken").asText(),session.body.path("account").path("accountId").asText(),session.body.path("account").path("profileId").asText());}
    Fixture fixture(boolean selected)throws Exception{var brand=user("brands");var creator=user("creators");
        var campaign=call("POST","/campaigns",brand.token,Map.of("title","Campaña","objective","Objetivo","category","Food","targetAudience","Lima"));assertEquals(201,campaign.status);
        String id=campaign.body.path("id").asText();var terms=Map.of("requirements",List.of(Map.of("description","Requisito","mandatory",false)),
                "deliverables",List.of(Map.of("contentType","VIDEO","description","Dos videos","quantity",2,"deadline",Instant.now().plusSeconds(7200).toString())),
                "applicationDeadline",Instant.now().plusSeconds(3600).toString(),"compensation",Map.of("type","CASH","amount",100,"currency","PEN","description","Pago por los videos"));
        assertEquals(200,call("PUT","/campaigns/"+id+"/conditions",brand.token,terms).status);assertEquals(200,call("POST","/campaigns/"+id+"/publication",brand.token,null).status);
        var app=call("POST","/campaigns/"+id+"/applications",creator.token,Map.of("message","Mi propuesta"));assertEquals(201,app.status);String application=app.body.path("id").asText();
        if(selected)assertEquals(200,call("POST","/campaigns/"+id+"/applications/"+application+"/decision",brand.token,Map.of("decision","SELECTED","expectedVersion",0)).status);
        return new Fixture(brand,creator,id,application);
    }
    Result proposal(Fixture f,User actor)throws Exception{return call("POST","/agreements",actor.token,Map.of("applicationId",f.application));}
    Result decision(JsonNode a,User user,String decision)throws Exception{return call("POST","/agreements/"+a.path("id").asText()+"/decisions",user.token,Map.of("termsVersion",a.path("snapshot").path("termsVersion").asText(),"decision",decision));}
    JsonNode active(Fixture f)throws Exception{var a=proposal(f,f.brand);assertEquals(201,a.status);assertEquals(200,decision(a.body,f.brand,"ACCEPT").status);var second=decision(a.body,f.creator,"ACCEPT");assertEquals(200,second.status);return second.body;}
    CollaborationService at(Instant time,Events sink){return new CollaborationService(participants,source,work,agreements,collaborations,reads,sink,Clock.fixed(time,ZoneOffset.UTC));}
    int count(String table,String field,String id){return jdbc.queryForObject("select count(*) from "+table+" where "+field+"=?",Integer.class,id);}

    @Test void bilateralHttpAcceptanceCreatesOneFrozenCollaborationWithRealObligations()throws Exception{
        var f=fixture(true);var a=proposal(f,f.creator);assertEquals(201,a.status);assertTrue(a.body.path("collaborationId").isNull());
        assertEquals(409,call("POST","/collaborations",f.brand.token,Map.of("agreementId",a.body.path("id").asText())).status);
        var first=decision(a.body,f.brand,"ACCEPT");assertEquals(200,first.status);assertEquals("PENDING",first.body.path("status").asText());
        var second=decision(a.body,f.creator,"ACCEPT");assertEquals(200,second.status);String c=second.body.path("collaborationId").asText();assertFalse(c.isBlank());
        var details=call("GET","/collaborations/"+c,f.creator.token,null);assertEquals(200,details.status);assertEquals("ACTIVE",details.body.path("status").asText());
        assertEquals(2,details.body.path("requiredQuantity").asInt());assertEquals(0,details.body.path("approvedQuantity").asInt());
        assertEquals("SUBMIT_DELIVERABLE",details.body.path("pendingActions").get(0).asText());assertTrue(details.body.path("availableActions").isEmpty());
        assertEquals(a.body.path("snapshot"),details.body.path("snapshot"));
        assertEquals("WAIT_FOR_DELIVERY",call("GET","/collaborations/"+c,f.brand.token,null).body.path("pendingActions").get(0).asText());
        assertEquals(c,decision(a.body,f.creator,"ACCEPT").body.path("collaborationId").asText());
        assertEquals(c,call("POST","/collaborations",f.brand.token,Map.of("agreementId",a.body.path("id").asText())).body.path("id").asText());
        assertEquals(1,count("collaboration_collaboration","agreement_id",a.body.path("id").asText()));assertEquals(1,count("collaboration_outbox","aggregate_id",c));
    }
    @Test void selectionIsRequiredAndForeignUsersCannotReadOrDecide()throws Exception{
        var f=fixture(false);assertEquals(409,proposal(f,f.creator).status);assertEquals(0,count("collaboration_agreement","application_id",f.application));
        var selected=fixture(true);var stranger=user("creators");assertEquals(403,proposal(selected,stranger).status);
        var a=proposal(selected,selected.brand);String id=a.body.path("id").asText();
        assertEquals(403,call("GET","/agreements/"+id,stranger.token,null).status);assertEquals(403,decision(a.body,stranger,"ACCEPT").status);
        assertEquals(401,call("GET","/agreements/"+id,null,null).status);
        assertEquals(404,call("GET","/agreements/"+UUID.randomUUID(),selected.brand.token,null).status);
        assertEquals(400,call("POST","/agreements/"+id+"/decisions",selected.brand.token,Map.of("decision","ACCEPT")).status);
        assertEquals(400,call("POST","/agreements",selected.brand.token,Map.of("applicationId",selected.application,"creatorAccepted",true)).status);
        var own=active(fixture(true));assertEquals(403,call("GET","/collaborations/"+own.path("collaborationId").asText(),stranger.token,null).status);
    }
    @Test void eitherPartyCanRejectButNeitherCanAcceptWrongTermsOrOverrideTheOther()throws Exception{
        for(boolean brandRejects:List.of(true,false)){
            var f=fixture(true);var a=proposal(f,f.brand);String id=a.body.path("id").asText();
            assertEquals(409,call("POST","/agreements/"+id+"/decisions",f.brand.token,Map.of("termsVersion",UUID.randomUUID().toString(),"decision","ACCEPT")).status);
            assertTrue(call("GET","/agreements/"+id,f.brand.token,null).body.path("brandAcceptedAt").isNull());
            User rejector=brandRejects?f.brand:f.creator;User other=brandRejects?f.creator:f.brand;
            assertEquals(200,decision(a.body,rejector,"REJECT").status);assertEquals(200,decision(a.body,rejector,"REJECT").status);
            assertEquals(409,decision(a.body,other,"ACCEPT").status);assertEquals(0,count("collaboration_collaboration","agreement_id",id));
        }
    }
    @Test void retriesAndSimultaneousConfirmationsKeepOneAgreementAndOneEvent()throws Exception{
        var f=fixture(true);var gate=new CyclicBarrier(2);JsonNode a;
        try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->{gate.await();return proposal(f,f.brand);});var two=pool.submit(()->{gate.await();return proposal(f,f.creator);});
            var x=one.get(30,TimeUnit.SECONDS);var y=two.get(30,TimeUnit.SECONDS);assertEquals(201,x.status);assertEquals(201,y.status);assertEquals(x.body.path("id"),y.body.path("id"));a=x.body;
        }
        var start=new CyclicBarrier(2);try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->{start.await();return decision(a,f.brand,"ACCEPT");});var two=pool.submit(()->{start.await();return decision(a,f.creator,"ACCEPT");});
            assertEquals(200,one.get(30,TimeUnit.SECONDS).status);assertEquals(200,two.get(30,TimeUnit.SECONDS).status);
        }
        assertEquals(1,count("collaboration_agreement","application_id",f.application));assertEquals(1,count("collaboration_collaboration","agreement_id",a.path("id").asText()));
        var finalState=call("GET","/agreements/"+a.path("id").asText(),f.creator.token,null);assertEquals("ACCEPTED",finalState.body.path("status").asText());
        assertEquals(1,count("collaboration_outbox","aggregate_id",finalState.body.path("collaborationId").asText()));
    }
    @Test void concurrentActivationRetriesAndOpposingDecisionsNeverProduceAnInvalidCollaboration()throws Exception{
        var f=fixture(true);var a=active(f);String agreement=a.path("id").asText();var gate=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->{gate.await();return call("POST","/collaborations",f.brand.token,Map.of("agreementId",agreement));});
            var second=pool.submit(()->{gate.await();return call("POST","/collaborations",f.creator.token,Map.of("agreementId",agreement));});
            var x=first.get(30,TimeUnit.SECONDS);var y=second.get(30,TimeUnit.SECONDS);
            assertEquals(201,x.status);assertEquals(201,y.status);assertEquals(x.body.path("id"),y.body.path("id"));
        }
        assertEquals(1,count("collaboration_collaboration","agreement_id",agreement));
        var pending=fixture(true);var proposal=proposal(pending,pending.brand).body;
        assertEquals(200,decision(proposal,pending.brand,"ACCEPT").status);var start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            var accept=pool.submit(()->{start.await();return decision(proposal,pending.creator,"ACCEPT");});
            var reject=pool.submit(()->{start.await();return decision(proposal,pending.creator,"REJECT");});
            var x=accept.get(30,TimeUnit.SECONDS);var y=reject.get(30,TimeUnit.SECONDS);
            assertEquals(Set.of(200,409),Set.of(x.status,y.status));
            var state=call("GET","/agreements/"+proposal.path("id").asText(),pending.creator.token,null).body;
            assertEquals(state.path("status").asText().equals("ACCEPTED")?1:0,count("collaboration_collaboration","agreement_id",proposal.path("id").asText()));
        }
    }
    @Test void failedActivationRollsBackTheSecondAcceptanceAndCanRecover()throws Exception{
        var f=fixture(true);var a=proposal(f,f.brand).body;assertEquals(200,decision(a,f.brand,"ACCEPT").status);
        var broken=at(Instant.now(),(id,type,terms,now)->{throw new IllegalStateException("Controlled event failure");});
        assertThrows(IllegalStateException.class,()->broken.decide(UUID.fromString(f.creator.account),UUID.fromString(a.path("id").asText()),UUID.fromString(a.path("snapshot").path("termsVersion").asText()),Agreement.Decision.ACCEPT));
        assertEquals(0,count("collaboration_collaboration","agreement_id",a.path("id").asText()));
        assertTrue(call("GET","/agreements/"+a.path("id").asText(),f.creator.token,null).body.path("creatorAcceptedAt").isNull());
        assertEquals(200,decision(a,f.creator,"ACCEPT").status);
    }
    @Test void frozenTermsSurviveProfileAndCampaignChangesAndClosedIntakeCanBeAccepted()throws Exception{
        var f=fixture(true);assertEquals(200,call("POST","/campaigns/"+f.campaign+"/closure",f.brand.token,null).status);
        var a=proposal(f,f.brand).body;var frozen=a.path("snapshot");
        jdbc.update("update campaign_campaign set title='Changed after agreement' where campaign_id=?",f.campaign);
        jdbc.update("update identity_creator_profile set display_name='New creator name' where profile_id=?",f.creator.profile);
        var again=proposal(f,f.brand);assertEquals(frozen,again.body.path("snapshot"));
        assertEquals(200,decision(a,f.brand,"ACCEPT").status);var accepted=decision(a,f.creator,"ACCEPT");assertEquals(200,accepted.status);
        assertEquals(frozen,call("GET","/collaborations/"+accepted.body.path("collaborationId").asText(),f.brand.token,null).body.path("snapshot"));
    }
    @Test void expiryIsVisibleBeforeWorkerAndPersistsWithoutLosingLateObligations()throws Exception{
        var f=fixture(true);var a=active(f);UUID c=UUID.fromString(a.path("collaborationId").asText());
        Instant deadline=Instant.parse(a.path("snapshot").path("acceptanceDeadline").asText());var future=at(deadline,events);
        var view=future.collaboration(UUID.fromString(f.creator.account),c);assertEquals(Collaboration.Status.EXPIRED,view.status());assertEquals(List.of("SUBMIT_LATE_DELIVERABLE"),view.pendingActions());
        assertEquals(2,view.requiredQuantity());assertEquals(1,future.collaborations(UUID.fromString(f.creator.account),Collaboration.Status.EXPIRED,new com.collabtech.platform.shared.application.pagination.PageRequest(0,20)).total());
        assertEquals(0,future.collaborations(UUID.fromString(f.creator.account),Collaboration.Status.ACTIVE,new com.collabtech.platform.shared.application.pagination.PageRequest(0,20)).total());
        future.expireDue();future.expireDue();assertEquals("EXPIRED",jdbc.queryForObject("select status from collaboration_collaboration where collaboration_id=?",String.class,c.toString()));
        assertEquals(2,count("collaboration_outbox","aggregate_id",c.toString()));
        var pending=fixture(true);var proposal=proposal(pending,pending.brand).body;Instant limit=Instant.parse(proposal.path("snapshot").path("acceptanceDeadline").asText());
        assertThrows(com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.class,()->at(limit,events).decide(UUID.fromString(pending.creator.account),UUID.fromString(proposal.path("id").asText()),UUID.fromString(proposal.path("snapshot").path("termsVersion").asText()),Agreement.Decision.ACCEPT));
        assertEquals("EXPIRED",jdbc.queryForObject("select status from collaboration_agreement where agreement_id=?",String.class,proposal.path("id").asText()));
    }
    @Test void listsArePagedFilteredPrivateAndInactiveParticipantsCannotActivate()throws Exception{
        var f=fixture(true);assertEquals(0,call("GET","/agreements/mine",f.creator.token,null).body.path("total").asInt());var a=proposal(f,f.brand).body;
        assertEquals(1,call("GET","/agreements/mine?status=PENDING&size=1",f.creator.token,null).body.path("total").asInt());
        assertEquals(0,call("GET","/agreements/mine",user("creators").token,null).body.path("total").asInt());
        assertEquals(400,call("GET","/collaborations/mine?page=-1",f.creator.token,null).status);
        assertEquals(400,call("GET","/agreements/mine?status=UNKNOWN",f.creator.token,null).status);
        assertEquals(200,decision(a,f.brand,"ACCEPT").status);jdbc.update("update identity_account set status='SUSPENDED' where account_id=?",f.brand.account);
        assertEquals(403,decision(a,f.creator,"ACCEPT").status);assertEquals(0,count("collaboration_collaboration","agreement_id",a.path("id").asText()));
    }
    @Test void outboxSurvivesConsumerFailureAndReceiptsPreventDuplicateEffects()throws Exception{
        var f=fixture(true);String c=active(f).path("collaborationId").asText();jdbc.execute("create table if not exists collaboration_test_probe(event_id char(36) primary key)");
        String name="probe."+UUID.randomUUID();var fail=new AtomicBoolean(true);
        CollaborationEventConsumer consumer=new CollaborationEventConsumer(){public String consumerName(){return name;}public void handle(Event event){
            if(!event.collaborationId().toString().equals(c)||!event.type().equals("CollaborationCreated"))return;
            jdbc.update("insert into collaboration_test_probe(event_id) values(?)",event.eventId().toString());if(fail.getAndSet(false))throw new IllegalStateException("Sensitive error must not be persisted");}};
        String event=jdbc.queryForObject("select event_id from collaboration_outbox where aggregate_id=? and event_type='CollaborationCreated'",String.class,c);
        events.dispatch(List.of());assertEquals(0,count("collaboration_event_receipt","event_id",event));
        int batches=jdbc.queryForObject("select count(*) from collaboration_outbox",Integer.class)/50+1;
        for(int i=0;i<batches&&count("collaboration_event_receipt","event_id",event)==0;i++)events.dispatch(List.of(consumer));
        assertEquals(0,count("collaboration_test_probe","event_id",event));
        assertEquals("CONSUMER_FAILED",jdbc.queryForObject("select last_error from collaboration_event_receipt where event_id=? and consumer=?",String.class,event,name));
        jdbc.update("update collaboration_event_receipt set next_attempt_at=? where event_id=? and consumer=?",Timestamp.from(Instant.now().minusSeconds(1)),event,name);
        var gate=new CyclicBarrier(2);try(var pool=Executors.newFixedThreadPool(2)){
            var x=pool.submit(()->{gate.await();events.dispatch(List.of(consumer));return true;});var y=pool.submit(()->{gate.await();events.dispatch(List.of(consumer));return true;});
            x.get(30,TimeUnit.SECONDS);y.get(30,TimeUnit.SECONDS);
        }
        events.dispatch(List.of(consumer));assertEquals(1,count("collaboration_test_probe","event_id",event));
        assertEquals(Boolean.TRUE,jdbc.queryForObject("select delivered from collaboration_event_receipt where event_id=? and consumer=?",Boolean.class,event,name));
    }
}
