package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.collaboration.application.services.DeliveryService;
import com.collabtech.platform.collaboration.application.ports.DeliveryPorts.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.infrastructure.files.DeliveryFileInspector;
import com.collabtech.platform.collaboration.infrastructure.persistence.DeliveryPersistence;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class DeliveryApiFixture extends CollaborationApiFixture {
    @Autowired DeliveryService delivery;
    @Autowired DeliveryPersistence data;
    @Autowired DeliveryFileInspector files;
    record Execution(Fixture fixture,String collaboration,String obligation,Instant deadline) {}
    Execution execution(int quantity)throws Exception {
        var f=fixture(true,quantity);var agreement=active(f);
        String id=agreement.path("collaborationId").asText();
        var view=call("GET","/collaborations/"+id,f.creator().token(),null).body();
        return new Execution(f,id,view.path("deliverables").get(0).path("deliverableId").asText(),
                Instant.parse(view.path("deliverables").get(0).path("deadline").asText()));
    }
    Map<String,Object> content(String key) {
        return Map.of("content",Map.of("url","https://example.com/content/"+key),
                "evidence",List.of(Map.of("url","https://example.com/evidence/"+key)),"notes","Contenido y evidencia");
    }
    DeliveryContent domainContent(String key) {
        return new DeliveryContent(new DeliveryContent.Reference("https://example.com/content/"+key,null),
                List.of(new DeliveryContent.Reference("https://example.com/evidence/"+key,null)),"Contenido y evidencia");
    }
    Map<String,Object> submission(Execution e,UUID request,String key) {
        return Map.of("requestId",request,"obligationId",e.obligation(),"content",content(key));
    }
    Result submit(Execution e,String key)throws Exception {
        return call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),submission(e,UUID.randomUUID(),key));
    }
    JsonNode piece(Result result) {
        assertEquals(201,result.status(),result.body().toString());
        return result.body().path("deliverable");
    }
    Map<String,Object> approval(JsonNode d,UUID request) {
        return Map.of("requestId",request,"submissionId",d.path("currentSubmission").path("id").asText(),"expectedVersion",d.path("version").asLong());
    }
    Map<String,Object> rejection(JsonNode d,UUID request,Instant deadline) {
        var body=new HashMap<String,Object>(approval(d,request));
        body.put("observations","Debe incluir el mensaje del producto");body.put("correctionDeadline",deadline);
        return body;
    }
    Result approve(Execution e,JsonNode d)throws Exception {
        return call("POST","/deliverables/"+d.path("id").asText()+"/validation",e.fixture().brand().token(),approval(d,UUID.randomUUID()));
    }
    JsonNode reject(Execution e,JsonNode d,Instant deadline)throws Exception {
        var result=call("POST","/deliverables/"+d.path("id").asText()+"/rejection",e.fixture().brand().token(),rejection(d,UUID.randomUUID(),deadline));
        assertEquals(200,result.status(),result.body().toString());
        return result.body().path("deliverable");
    }
    JsonNode collaboration(Execution e,User actor)throws Exception {
        var result=call("GET","/collaborations/"+e.collaboration(),actor.token(),null);assertEquals(200,result.status());
        return result.body();
    }
    DeliveryService atDelivery(Instant now,DeliveryEvents sink) {
        return new DeliveryService(participants,work,collaborations,data,data,data,files,data,sink,Clock.fixed(now,ZoneOffset.UTC));
    }
    int events(Execution e,String type) {
        return jdbc.queryForObject("select count(*) from collaboration_outbox where aggregate_id=? and event_type=?",Integer.class,e.collaboration(),type);
    }

    Result upload(Execution e,User user,UUID request,String name,String media,byte[] bytes)throws Exception {
        String boundary="boundary"+UUID.randomUUID();var prefix=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+media+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        var suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/collaborations/"+e.collaboration()+"/assets"))
                .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+user.token()).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(request!=null)req.header("Idempotency-Key",request.toString());
        var response=http.send(req.POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),
                HttpRequest.BodyPublishers.ofByteArray(bytes),HttpRequest.BodyPublishers.ofByteArray(suffix))).build(),HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(),json.readTree(response.body()));
    }
}
