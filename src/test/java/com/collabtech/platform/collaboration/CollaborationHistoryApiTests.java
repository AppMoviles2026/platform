package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.collaboration.infrastructure.configuration.CollaborationHistoryBackfill;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CollaborationHistoryApiTests extends IncidentApiFixture {
    @Test void historyListsEmptyAndExistingParticipationsForBothRolesAndKeepsOriginalNames()throws Exception {
        var empty=user("creators");
        assertEquals(0,call("GET","/collaborations/history",empty.token(),null).body().path("total").asInt());
        var e=execution(1);var before=collaboration(e,e.fixture().creator());
        jdbc.update("update identity_brand_profile set business_name='Changed business name' where profile_id=?",e.fixture().brand().profile());
        jdbc.update("update campaign_campaign set title='Changed campaign title' where campaign_id=?",e.fixture().campaign());
        for(var actor:List.of(e.fixture().brand(),e.fixture().creator())) {
            var list=call("GET","/collaborations/history?size=1",actor.token(),null);
            assertEquals(200,list.status());assertEquals(1,list.body().path("total").asInt());
            var detail=call("GET","/collaborations/history/"+e.collaboration(),actor.token(),null);
            assertEquals(200,detail.status());
            assertEquals(before.path("snapshot"),detail.body().path("collaboration").path("snapshot"));
            assertEquals(before.path("snapshot"),detail.body().path("agreement").path("snapshot"));
            assertEquals("NOT_AVAILABLE",detail.body().path("references").path("compensation").path("availability").asText());
            assertTrue(detail.body().path("references").path("compensation").path("resourceId").isNull());
            assertEquals("NOT_AVAILABLE",detail.body().path("references").path("performance").path("availability").asText());
            assertEquals(200,call("GET",detail.body().path("resources").path("deliveries").asText().replace("/api/v1",""),actor.token(),null).status());
            assertEquals(200,call("GET",detail.body().path("resources").path("incidents").asText().replace("/api/v1",""),actor.token(),null).status());
        }
        assertEquals(403,call("GET","/collaborations/history/"+e.collaboration(),empty.token(),null).status());
        assertEquals(403,call("GET","/collaborations/"+e.collaboration()+"/history",empty.token(),null).status());
        assertEquals(401,call("GET","/collaborations/history",null,null).status());
        assertEquals(400,call("GET","/collaborations/history?page=-1",e.fixture().creator().token(),null).status());
        assertEquals(400,call("GET","/collaborations/history?status=UNKNOWN",e.fixture().brand().token(),null).status());
    }

    @Test void timelineIncludesRealActorsDeliveryVersionsAndResolutionsWithoutDuplicatingRetries()throws Exception {
        var e=execution(1);var d=piece(submit(e,"timeline"));
        var claim=openIncident(e,e.fixture().creator(),"Aclarar el mensaje publicado");
        var proposed=propose(e,claim,e.fixture().brand(),keepTerms("El mensaje cumple el acuerdo"));
        UUID request=UUID.randomUUID();
        assertEquals(200,decide(proposed,e.fixture().creator(),"ACCEPT",request).status());
        assertEquals(200,decide(proposed,e.fixture().creator(),"ACCEPT",request).status());
        assertEquals(200,approve(e,d).status());
        var page=call("GET","/collaborations/"+e.collaboration()+"/history?size=100",e.fixture().brand().token(),null);
        assertEquals(200,page.status());
        var types=new HashSet<String>();long sequence=0;int resolved=0;
        for(var entry:page.body().path("items")) {
            assertTrue(entry.path("sequence").asLong()>sequence);sequence=entry.path("sequence").asLong();
            types.add(entry.path("type").asText());
            if(entry.path("type").asText().equals("DeliverableSubmitted")) {
                assertEquals(e.fixture().creator().account(),entry.path("actorAccountId").asText());
                assertFalse(entry.path("reconstructed").asBoolean());
            }
            if(entry.path("type").asText().equals("IncidentResolved"))resolved++;
        }
        assertTrue(types.containsAll(Set.of("AgreementGenerated","BrandTermsAccepted","CreatorTermsAccepted","CollaborationCreated",
                "DeliverableSubmitted","IncidentOpened","ResolutionProposed","IncidentResolved","CollaborationCompleted")));
        assertEquals(1,resolved);
        var again=call("GET","/collaborations/"+e.collaboration()+"/history?size=1&page=1",e.fixture().creator().token(),null);
        assertEquals(page.body().path("total"),again.body().path("total"));
        assertEquals(1,again.body().path("items").size());
        assertEquals(0,call("GET","/collaborations/history?status=ACTIVE",e.fixture().creator().token(),null).body().path("total").asInt());
        assertEquals(1,call("GET","/collaborations/history?status=COMPLETED",e.fixture().creator().token(),null).body().path("total").asInt());
    }

    @Test void historyBackfillUsesPersistedFactsAndIsSafeToRepeatOrRunConcurrently()throws Exception {
        var e=execution(1);var d=piece(submit(e,"legacy"));approve(e,d);
        UUID id=UUID.fromString(e.collaboration());
        long before=jdbc.queryForObject("select count(*) from collaboration_history where collaboration_id=?",Long.class,e.collaboration());
        jdbc.update("delete from collaboration_history where collaboration_id=?",e.collaboration());
        jdbc.update("update collaboration_collaboration set history_initialized=false,history_sequence=0 where collaboration_id=?",e.collaboration());
        var gate=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();return work.execute(()->{history.initialize(id);return true;});});
            var b=pool.submit(()->{gate.await();return work.execute(()->{history.initialize(id);return true;});});
            assertTrue(a.get(30,TimeUnit.SECONDS));assertTrue(b.get(30,TimeUnit.SECONDS));
        }
        new CollaborationHistoryBackfill(history,work).run(new DefaultApplicationArguments());
        var page=call("GET","/collaborations/"+e.collaboration()+"/history?size=100",e.fixture().creator().token(),null);
        assertEquals(before,page.body().path("total").asLong());
        assertEquals(before,jdbc.queryForObject("select count(distinct sequence_number) from collaboration_history where collaboration_id=?",Long.class,e.collaboration()));
        for(var item:page.body().path("items"))assertTrue(item.path("reconstructed").asBoolean());
        assertEquals("COMPLETED",collaboration(e,e.fixture().creator()).path("status").asText());
    }

    @Test void publishedExecutionFactsRequireATransactionForLockedReads()throws Exception {
        var e=execution(1);var id=UUID.fromString(e.collaboration());
        assertThrows(IllegalTransactionStateException.class,()->executionFacts.findForUpdate(id));
        var facts=work.execute(()->executionFacts.findForUpdate(id).orElseThrow());
        assertEquals(e.fixture().brand().profile(),facts.brandProfileId().toString());
        assertEquals(e.fixture().creator().profile(),facts.creatorProfileId().toString());
        assertFalse(facts.fulfilled());assertFalse(facts.compensationEligible());
        assertEquals("CASH",facts.compensation().type());
    }

    @Test void multipartIncidentEvidenceWorksForTheCompanyWhileTheCollaborationIsDisputed()throws Exception {
        var e=execution(1);openIncident(e,e.fixture().creator(),"Permitir evidencia de ambas partes");
        String boundary="claim"+UUID.randomUUID();byte[] bytes="%PDF-1.7 incident".getBytes(StandardCharsets.US_ASCII);
        var prefix=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"claim.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        var suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/collaborations/"+e.collaboration()+"/incident-assets"))
                .header("Authorization","Bearer "+e.fixture().brand().token()).header("Idempotency-Key",UUID.randomUUID().toString())
                .header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),
                        HttpRequest.BodyPublishers.ofByteArray(bytes),HttpRequest.BodyPublishers.ofByteArray(suffix))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(201,response.statusCode(),response.body());
        var asset=json.readTree(response.body()).path("asset").path("id").asText();
        assertEquals(403,call("GET","/collaboration-assets/"+asset,e.fixture().creator().token(),null).status());
        assertFalse(call("GET","/collaborations/"+e.collaboration()+"/history?size=100",e.fixture().creator().token(),null).body().toString().contains("claim.pdf"));
        assertEquals(200,call("GET","/collaboration-assets/"+asset,e.fixture().brand().token(),null).status());
    }
}
