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
abstract class CollaborationApiFixture {
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
    Fixture fixture(boolean selected)throws Exception{return fixture(selected,2);}
    Fixture fixture(boolean selected,int quantity)throws Exception{var brand=user("brands");var creator=user("creators");
        var campaign=call("POST","/campaigns",brand.token,Map.of("title","Campaña","objective","Objetivo","category","Food","targetAudience","Lima"));assertEquals(201,campaign.status);
        String id=campaign.body.path("id").asText();var terms=Map.of("requirements",List.of(Map.of("description","Requisito","mandatory",false)),
                "deliverables",List.of(Map.of("contentType","VIDEO","description","Dos videos","quantity",quantity,"deadline",Instant.now().plusSeconds(7200).toString())),
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

}
