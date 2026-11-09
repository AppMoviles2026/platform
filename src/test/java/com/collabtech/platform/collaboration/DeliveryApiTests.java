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
class DeliveryApiTests extends CollaborationApiFixture {
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

    @Test void eachRequiredPieceMustBeApprovedBeforeCompletionAndApprovalNeverFabricatesPayment()throws Exception {
        var e=execution(2);
        assertEquals(0,call("GET","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().brand().token(),null).body().path("total").asInt());
        var one=piece(submit(e,"one"));var two=piece(submit(e,"two"));
        assertEquals("PENDING_VALIDATION",one.path("status").asText());
        assertFalse(one.path("currentSubmission").path("late").asBoolean());
        var view=collaboration(e,e.fixture().creator());
        assertEquals(2,view.path("submittedQuantity").asInt());assertEquals(0,view.path("approvedQuantity").asInt());
        assertEquals("UNDER_REVIEW",view.path("status").asText());assertTrue(view.path("nextDeadline").isNull());
        assertEquals("WAIT_FOR_REVIEW",view.path("pendingActions").get(0).asText());
        assertEquals("REVIEW_DELIVERABLE",collaboration(e,e.fixture().brand()).path("availableActions").get(0).asText());
        assertEquals(409,submit(e,"third").status());
        assertEquals(200,approve(e,one).status());
        assertEquals(1,collaboration(e,e.fixture().creator()).path("approvedQuantity").asInt());
        assertTrue(collaboration(e,e.fixture().brand()).path("completedAt").isNull());
        assertEquals(200,approve(e,two).status());
        var completed=collaboration(e,e.fixture().brand());assertEquals("COMPLETED",completed.path("status").asText());
        assertEquals(2,completed.path("approvedQuantity").asInt());assertFalse(completed.path("completedAt").isNull());
        assertTrue(completed.path("availableActions").isEmpty());
        assertEquals(1,events(e,"CollaborationCompleted"));assertEquals(2,events(e,"DeliverableApproved"));
        assertEquals(0,events(e,"CompensationPaid"));
        assertEquals(409,submit(e,"after-completion").status());
    }

    @Test void rejectionAndCorrectionPreserveEveryVersionAndAnOldReviewCannotApproveNewContent()throws Exception {
        var e=execution(1);var one=piece(submit(e,"original"));String id=one.path("id").asText();
        var rejected=reject(e,one,Instant.now().plusSeconds(86400));
        assertEquals("REJECTED",rejected.path("status").asText());
        assertTrue(collaboration(e,e.fixture().creator()).path("availableActions").toString().contains("SUBMIT_REVISION"));
        var correction=Map.of("requestId",UUID.randomUUID(),"expectedVersion",rejected.path("version").asLong(),"content",content("corrected"));
        var revised=piece(call("POST","/deliverables/"+id+"/versions",e.fixture().creator().token(),correction));
        assertEquals(2,revised.path("currentSubmission").path("revision").asInt());assertTrue(revised.path("currentReview").isNull());
        assertEquals("PENDING_VALIDATION",revised.path("status").asText());
        var stale=new HashMap<String,Object>(approval(revised,UUID.randomUUID()));
        stale.put("submissionId",one.path("currentSubmission").path("id").asText());
        var result=call("POST","/deliverables/"+id+"/validation",e.fixture().brand().token(),stale);
        assertEquals(409,result.status());assertEquals("STALE_SUBMISSION",result.body().path("code").asText());
        assertEquals(409,approve(e,one).status());
        var history=call("GET","/deliverables/"+id+"/versions?size=1",e.fixture().creator().token(),null);
        assertEquals(2,history.body().path("total").asInt());assertEquals(2,history.body().path("items").get(0).path("submission").path("revision").asInt());
        var original=call("GET","/deliverables/"+id+"/versions?page=1&size=1",e.fixture().brand().token(),null).body().path("items").get(0);
        assertEquals("https://example.com/content/original",original.path("submission").path("content").path("content").path("url").asText());
        assertEquals("REJECTED",original.path("review").path("decision").asText());
        assertEquals("Debe incluir el mensaje del producto",original.path("review").path("observations").asText());
        assertEquals(200,approve(e,revised).status());
        assertEquals(1,events(e,"DeliverableRevisionSubmitted"));
    }

    @Test void rejectionWithoutObservationsOrFutureCorrectionDeadlineIsAtomic()throws Exception {
        var e=execution(1);var d=piece(submit(e,"invalid-rejection"));String path="/deliverables/"+d.path("id").asText()+"/rejection";
        var missing=new HashMap<String,Object>(approval(d,UUID.randomUUID()));missing.put("observations","Motivo");
        assertEquals(400,call("POST",path,e.fixture().brand().token(),missing).status());
        var blank=new HashMap<String,Object>(rejection(d,UUID.randomUUID(),Instant.now().plusSeconds(60)));blank.put("observations"," ");
        assertEquals(400,call("POST",path,e.fixture().brand().token(),blank).status());
        assertEquals(400,call("POST",path,e.fixture().brand().token(),rejection(d,UUID.randomUUID(),Instant.now().minusSeconds(1))).status());
        var unchanged=call("GET","/deliverables/"+d.path("id").asText(),e.fixture().creator().token(),null).body();
        assertEquals(0,unchanged.path("version").asInt());assertEquals("PENDING_VALIDATION",unchanged.path("status").asText());
        assertEquals(0,events(e,"DeliverableRejected"));
    }

    @Test void lateSubmissionsRemainReviewableAndCorrectionDeadlinesCanExpireIndependently()throws Exception {
        var e=execution(1);UUID creator=UUID.fromString(e.fixture().creator().account());UUID collab=UUID.fromString(e.collaboration());
        var late=atDelivery(e.deadline(),events).submit(creator,collab,new DeliveryService.Submit(UUID.randomUUID(),UUID.fromString(e.obligation()),domainContent("late")));
        assertTrue(late.deliverable().currentSubmission().late());
        assertEquals(1,events(e,"CollaborationExpired"));
        var pending=at(e.deadline().plusSeconds(1),events).collaboration(creator,collab);
        assertEquals(Collaboration.Status.UNDER_REVIEW,pending.status());assertNull(pending.nextDeadline());assertNotNull(pending.expiredAt());
        var brand=UUID.fromString(e.fixture().brand().account());var correction=e.deadline().plusSeconds(100);
        var rejected=atDelivery(e.deadline().plusSeconds(2),events).validate(brand,late.deliverable().id(),
                new DeliveryService.Validation(UUID.randomUUID(),late.affectedSubmissionId(),late.deliverable().version(),"Corregir el mensaje",correction),Deliverable.Decision.REJECTED);
        var future=at(correction,events);future.expireDue();future.expireDue();
        assertEquals(2,events(e,"CollaborationExpired"));
        assertEquals(Collaboration.Status.EXPIRED,future.collaboration(creator,collab).status());
        var body=new DeliveryService.Correction(UUID.randomUUID(),rejected.deliverable().version(),domainContent("too-late"));
        var error=assertThrows(CollaborationFailure.class,()->atDelivery(correction,events).correct(creator,rejected.deliverable().id(),body));
        assertEquals("CORRECTION_WINDOW_CLOSED",error.code().name());
        assertEquals(1,data.revisions(rejected.deliverable().id(),new com.collabtech.platform.shared.application.pagination.PageRequest(0,20)).total());
    }

    @Test void anOnTimeSubmissionDoesNotExpireWhileWaitingForTheCompany()throws Exception {
        var e=execution(1);var d=piece(submit(e,"on-time"));var future=at(e.deadline().plusSeconds(3600),events);
        assertEquals(Collaboration.Status.UNDER_REVIEW,future.collaboration(UUID.fromString(e.fixture().creator().account()),UUID.fromString(e.collaboration())).status());
        future.expireDue();assertEquals(0,events(e,"CollaborationExpired"));
        assertEquals(200,approve(e,d).status());
    }

    @Test void replayKeysProtectSubmissionsRevisionsAndReviewsEvenAfterTheResourceChanges()throws Exception {
        var e=execution(1);UUID request=UUID.randomUUID();var body=submission(e,request,"retry");
        var original=call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),body);
        var repeated=call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),body);
        assertEquals(piece(original).path("id"),piece(repeated).path("id"));assertTrue(repeated.body().path("replayed").asBoolean());
        assertEquals(409,call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),submission(e,request,"different")).status());
        var d=original.body().path("deliverable");UUID rejectKey=UUID.randomUUID();
        var rejectBody=rejection(d,rejectKey,Instant.now().plusSeconds(86400));
        var rejection=call("POST","/deliverables/"+d.path("id").asText()+"/rejection",e.fixture().brand().token(),rejectBody);
        assertEquals(200,rejection.status());d=rejection.body().path("deliverable");
        var correction=Map.of("requestId",UUID.randomUUID(),"expectedVersion",d.path("version").asLong(),"content",content("v2"));
        var first=call("POST","/deliverables/"+d.path("id").asText()+"/versions",e.fixture().creator().token(),correction);
        var second=call("POST","/deliverables/"+d.path("id").asText()+"/versions",e.fixture().creator().token(),correction);
        assertEquals(piece(first).path("currentSubmission"),piece(second).path("currentSubmission"));
        var replayRejection=call("POST","/deliverables/"+d.path("id").asText()+"/rejection",e.fixture().brand().token(),rejectBody);
        assertEquals(200,replayRejection.status());assertEquals("PENDING_VALIDATION",replayRejection.body().path("deliverable").path("status").asText());
        assertEquals(rejectBody.get("submissionId"),replayRejection.body().path("affectedSubmissionId").asText());
        var replayOriginal=call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),body);
        assertEquals(original.body().path("affectedSubmissionId"),replayOriginal.body().path("affectedSubmissionId"));
        assertEquals(2,replayOriginal.body().path("deliverable").path("currentSubmission").path("revision").asInt());
        assertEquals(1,events(e,"DeliverableSubmitted"));assertEquals(1,events(e,"DeliverableRejected"));assertEquals(1,events(e,"DeliverableRevisionSubmitted"));
    }

    @Test void concurrentSubmissionRetriesAndQuantityLimitsKeepExactlyTheRequiredPieces()throws Exception {
        var e=execution(1);var body=submission(e,UUID.randomUUID(),"same");var gate=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();return call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),body);});
            var b=pool.submit(()->{gate.await();return call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),body);});
            assertEquals(piece(a.get(30,TimeUnit.SECONDS)).path("id"),piece(b.get(30,TimeUnit.SECONDS)).path("id"));
        }
        assertEquals(1,count("collaboration_deliverable","collaboration_id",e.collaboration()));assertEquals(1,events(e,"DeliverableSubmitted"));
        var other=execution(1);var start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return submit(other,"a");});var b=pool.submit(()->{start.await();return submit(other,"b");});
            assertEquals(Set.of(201,409),Set.of(a.get(30,TimeUnit.SECONDS).status(),b.get(30,TimeUnit.SECONDS).status()));
        }
        assertEquals(1,count("collaboration_deliverable","collaboration_id",other.collaboration()));
    }

    @Test void competingCompanyDecisionsAndCorrectionsCannotOverwriteOneAnother()throws Exception {
        var e=execution(1);var d=piece(submit(e,"decision-race"));var gate=new CyclicBarrier(2);String id=d.path("id").asText();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();return approve(e,d);});
            var b=pool.submit(()->{gate.await();return call("POST","/deliverables/"+id+"/rejection",e.fixture().brand().token(),rejection(d,UUID.randomUUID(),Instant.now().plusSeconds(86400)));});
            assertEquals(Set.of(200,409),Set.of(a.get(30,TimeUnit.SECONDS).status(),b.get(30,TimeUnit.SECONDS).status()));
        }
        assertEquals(1,events(e,"DeliverableApproved")+events(e,"DeliverableRejected"));
        var other=execution(1);var rejected=reject(other,piece(submit(other,"correction-race")),Instant.now().plusSeconds(86400));
        String target=rejected.path("id").asText();var start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return call("POST","/deliverables/"+target+"/versions",other.fixture().creator().token(),Map.of("requestId",UUID.randomUUID(),"expectedVersion",rejected.path("version").asLong(),"content",content("a")));});
            var b=pool.submit(()->{start.await();return call("POST","/deliverables/"+target+"/versions",other.fixture().creator().token(),Map.of("requestId",UUID.randomUUID(),"expectedVersion",rejected.path("version").asLong(),"content",content("b")));});
            assertEquals(Set.of(201,409),Set.of(a.get(30,TimeUnit.SECONDS).status(),b.get(30,TimeUnit.SECONDS).status()));
        }
        assertEquals(2,data.revisions(UUID.fromString(target),new com.collabtech.platform.shared.application.pagination.PageRequest(0,20)).total());
    }

    @Test void failedExecutionEventRollsBackSubmissionProgressAndRequestThenSameRequestCanRecover()throws Exception {
        var e=execution(1);var creator=UUID.fromString(e.fixture().creator().account());var collab=UUID.fromString(e.collaboration());
        var body=new DeliveryService.Submit(UUID.randomUUID(),UUID.fromString(e.obligation()),domainContent("recover"));
        var broken=atDelivery(Instant.now(),event->{throw new IllegalStateException("Controlled failure");});
        assertThrows(IllegalStateException.class,()->broken.submit(creator,collab,body));
        assertEquals(0,count("collaboration_deliverable","collaboration_id",e.collaboration()));
        assertEquals(0,count("collaboration_delivery_command","collaboration_id",e.collaboration()));
        assertEquals(0,collaboration(e,e.fixture().creator()).path("submittedQuantity").asInt());
        assertEquals("ACTIVE",collaboration(e,e.fixture().creator()).path("status").asText());
        var result=delivery.submit(creator,collab,body);assertFalse(result.replayed());
        assertEquals(1,events(e,"DeliverableSubmitted"));
        var review=new DeliveryService.Validation(UUID.randomUUID(),result.affectedSubmissionId(),result.deliverable().version(),null,null);
        assertThrows(IllegalStateException.class,()->broken.validate(UUID.fromString(e.fixture().brand().account()),result.deliverable().id(),review,Deliverable.Decision.APPROVED));
        assertEquals("PENDING_VALIDATION",delivery.get(creator,result.deliverable().id()).status().name());
        assertEquals(0,events(e,"CollaborationCompleted"));
        assertEquals(Deliverable.Status.APPROVED,delivery.validate(UUID.fromString(e.fixture().brand().account()),result.deliverable().id(),review,Deliverable.Decision.APPROVED).deliverable().status());
    }

    @Test void jwtRolesAndParticipantOwnershipProtectEveryExecutionEndpoint()throws Exception {
        var e=execution(1);var foreign=user("creators");var otherBrand=user("brands");var d=piece(submit(e,"private"));
        String root="/collaborations/"+e.collaboration()+"/deliverables",id=d.path("id").asText();
        assertEquals(401,call("GET",root,null,null).status());
        assertEquals(403,call("GET",root,foreign.token(),null).status());
        assertEquals(403,call("GET","/deliverables/"+id,otherBrand.token(),null).status());
        assertEquals(403,call("GET","/deliverables/"+id+"/versions",foreign.token(),null).status());
        assertEquals(403,call("POST",root,e.fixture().brand().token(),submission(e,UUID.randomUUID(),"forged")).status());
        assertEquals(403,call("POST","/deliverables/"+id+"/validation",e.fixture().creator().token(),approval(d,UUID.randomUUID())).status());
        assertEquals(403,call("POST","/deliverables/"+id+"/rejection",foreign.token(),rejection(d,UUID.randomUUID(),Instant.now().plusSeconds(60))).status());
        assertEquals(403,call("POST","/deliverables/"+id+"/versions",e.fixture().brand().token(),Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"content",content("forged"))).status());
        assertEquals(404,call("GET","/deliverables/"+UUID.randomUUID(),e.fixture().creator().token(),null).status());
    }

    @Test void invalidPayloadsAndPagedFiltersDoNotCreatePartialRecords()throws Exception {
        var e=execution(2);String root="/collaborations/"+e.collaboration()+"/deliverables";
        var missing=Map.of("obligationId",e.obligation(),"content",content("missing-request"));
        assertEquals(400,call("POST",root,e.fixture().creator().token(),missing).status());
        var invalid=new HashMap<String,Object>(submission(e,UUID.randomUUID(),"bad"));invalid.put("status","APPROVED");
        assertEquals(400,call("POST",root,e.fixture().creator().token(),invalid).status());
        invalid=new HashMap<>(submission(e,UUID.randomUUID(),"bad"));invalid.put("obligationId",UUID.randomUUID());
        assertEquals(404,call("POST",root,e.fixture().creator().token(),invalid).status());
        invalid=new HashMap<>(submission(e,UUID.randomUUID(),"bad"));invalid.put("content",Map.of("content",Map.of("url","https://example.com"),"evidence",List.of()));
        assertEquals(400,call("POST",root,e.fixture().creator().token(),invalid).status());
        assertEquals(0,count("collaboration_deliverable","collaboration_id",e.collaboration()));
        var one=piece(submit(e,"page-one"));piece(submit(e,"page-two"));approve(e,one);
        assertEquals(1,call("GET",root+"?status=APPROVED&size=1",e.fixture().brand().token(),null).body().path("total").asInt());
        assertEquals(1,call("GET",root+"?status=PENDING_VALIDATION",e.fixture().creator().token(),null).body().path("items").size());
        assertEquals(0,call("GET",root+"?page=10&size=1",e.fixture().creator().token(),null).body().path("items").size());
        assertEquals(400,call("GET",root+"?size=101",e.fixture().creator().token(),null).status());
        assertEquals(400,call("GET",root+"?status=unknown",e.fixture().creator().token(),null).status());
        assertEquals(400,call("GET","/deliverables/"+one.path("id").asText()+"/versions?page=-1",e.fixture().creator().token(),null).status());
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
    @Test void uploadedEvidencePersistsPrivatelyAndBothParticipantsCanReadSubmittedHistoricalFiles()throws Exception {
        var e=execution(1);var pdf="%PDF-1.7\nEvidencia de demostracion\n%%EOF".getBytes(StandardCharsets.US_ASCII);UUID request=UUID.randomUUID();
        var uploaded=upload(e,e.fixture().creator(),request,"evidencia.pdf","application/pdf",pdf);
        assertEquals(201,uploaded.status(),uploaded.body().toString());String asset=uploaded.body().path("asset").path("id").asText();
        assertEquals(asset,upload(e,e.fixture().creator(),request,"evidencia.pdf","application/pdf",pdf).body().path("asset").path("id").asText());
        assertEquals(409,upload(e,e.fixture().creator(),request,"otra.pdf","application/pdf",pdf).status());
        assertEquals(403,call("GET","/collaboration-assets/"+asset,e.fixture().brand().token(),null).status());
        assertEquals(200,call("GET","/collaboration-assets/"+asset,e.fixture().creator().token(),null).status());
        var withFile=Map.of("content",Map.of("url","https://example.com/published"),"evidence",List.of(Map.of("assetId",asset)),"notes","Captura");
        var created=call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),
                Map.of("requestId",UUID.randomUUID(),"obligationId",e.obligation(),"content",withFile));
        var d=piece(created);
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/collaboration-assets/"+asset+"/content"))
                .header("Authorization","Bearer "+e.fixture().brand().token()).GET().build();
        var download=http.send(req,HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,download.statusCode());assertArrayEquals(pdf,download.body());
        assertTrue(download.headers().firstValue("Content-Disposition").orElseThrow().contains("attachment"));
        assertEquals("nosniff",download.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals(403,call("GET","/collaboration-assets/"+asset,user("creators").token(),null).status());
        var rejected=reject(e,d,Instant.now().plusSeconds(86400));
        piece(call("POST","/deliverables/"+d.path("id").asText()+"/versions",e.fixture().creator().token(),
                Map.of("requestId",UUID.randomUUID(),"expectedVersion",rejected.path("version").asLong(),"content",content("replace-file"))));
        assertArrayEquals(pdf,http.send(req,HttpResponse.BodyHandlers.ofByteArray()).body());
        var other=execution(1);
        assertEquals(403,call("POST","/collaborations/"+other.collaboration()+"/deliverables",other.fixture().creator().token(),
                Map.of("requestId",UUID.randomUUID(),"obligationId",other.obligation(),"content",withFile)).status());
    }

    @Test void invalidFilesMissingKeysAndOversizedUploadsAreRejectedWithoutAssets()throws Exception {
        var e=execution(1);var pdf="%PDF-1.7 demo".getBytes(StandardCharsets.US_ASCII);
        assertEquals(403,upload(e,e.fixture().brand(),UUID.randomUUID(),"file.pdf","application/pdf",pdf).status());
        assertEquals(400,upload(e,e.fixture().creator(),null,"file.pdf","application/pdf",pdf).status());
        assertEquals(415,upload(e,e.fixture().creator(),UUID.randomUUID(),"file.png","image/png",pdf).status());
        assertEquals(415,upload(e,e.fixture().creator(),UUID.randomUUID(),"file.html","text/html","<html>x</html>".getBytes()).status());
        assertEquals(400,upload(e,e.fixture().creator(),UUID.randomUUID(),"file.pdf","application/pdf",new byte[0]).status());
        byte[] huge=new byte[10485761];System.arraycopy(pdf,0,huge,0,pdf.length);
        assertEquals(413,upload(e,e.fixture().creator(),UUID.randomUUID(),"large.pdf","application/pdf",huge).status());
        assertEquals(0,count("collaboration_asset","collaboration_id",e.collaboration()));
    }


    @Test void failedAssetStorageLeavesNoBinaryOrRequestAndRecoversWithTheSameKey()throws Exception {
        var e=execution(1);var bytes="%PDF-1.7 demo".getBytes(StandardCharsets.US_ASCII);
        Attachments broken=new Attachments() {
            public Optional<Asset> metadata(UUID id){return data.metadata(id);}
            public byte[] bytes(UUID id){return data.bytes(id);}
            public boolean referenced(UUID id){return data.referenced(id);}
            public void save(Asset asset,byte[] content){data.save(asset,content);throw new IllegalStateException("Controlled storage failure");}
        };
        var failure=new DeliveryService(participants,work,collaborations,data,data,broken,files,data,events,Clock.systemUTC());
        UUID request=UUID.randomUUID(),creator=UUID.fromString(e.fixture().creator().account()),collab=UUID.fromString(e.collaboration());
        assertThrows(IllegalStateException.class,()->failure.upload(creator,collab,request,"file.pdf","application/pdf",bytes));
        assertEquals(0,count("collaboration_asset","collaboration_id",e.collaboration()));
        assertEquals(0,count("collaboration_delivery_command","collaboration_id",e.collaboration()));
        var asset=delivery.upload(creator,collab,request,"file.pdf","application/pdf",bytes);
        assertArrayEquals(bytes,delivery.download(creator,asset.asset().id()).bytes());
        var body=Map.of("requestId",UUID.randomUUID(),"obligationId",e.obligation(),"content",
                Map.of("content",Map.of("assetId",asset.asset().id()),"evidence",List.of(Map.of("url","https://example.com/evidence"))));
        var d=piece(call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),body));
        assertEquals(200,approve(e,d).status());
        assertEquals(asset.asset().id(),delivery.upload(creator,collab,request,"file.pdf","application/pdf",bytes).asset().id());
    }

    @Test void anotherPiecesExpiredCorrectionDoesNotHideAValidCorrectionAndOldProgressJsonIsReadable()throws Exception {
        var e=execution(2);var raw=jdbc.queryForObject("select obligations from collaboration_collaboration where collaboration_id=?",String.class,e.collaboration());
        var legacy=json.readTree(raw);
        for(var node:legacy) {
            var object=(tools.jackson.databind.node.ObjectNode)node;
            for(String field:List.of("submittedQuantity","pendingQuantity","rejectedQuantity","correctionDeadline","latestCorrectionDeadline"))object.remove(field);
        }
        jdbc.update("update collaboration_collaboration set obligations=? where collaboration_id=?",json.writeValueAsString(legacy),e.collaboration());
        assertEquals(0,collaboration(e,e.fixture().creator()).path("submittedQuantity").asInt());
        var one=piece(submit(e,"first-window"));var two=piece(submit(e,"second-window"));Instant now=Instant.now();
        reject(e,one,now.plusSeconds(100));
        var second=reject(e,two,now.plusSeconds(200));
        var clock=at(now.plusSeconds(150),events);
        var view=clock.collaboration(UUID.fromString(e.fixture().creator().account()),UUID.fromString(e.collaboration()));
        assertEquals(Collaboration.Status.EXPIRED,view.status());
        assertTrue(view.availableActions().contains("SUBMIT_REVISION"));
        var correction=new DeliveryService.Correction(UUID.randomUUID(),second.path("version").asLong(),domainContent("valid-window"));
        assertEquals(Deliverable.Status.PENDING_VALIDATION,atDelivery(now.plusSeconds(150),events)
                .correct(UUID.fromString(e.fixture().creator().account()),UUID.fromString(second.path("id").asText()),correction).deliverable().status());
    }

    @Test void cancelledAndDisputedCollaborationsBlockExecutionButStillAllowPrivateQueries()throws Exception {
        for(String status:List.of("CANCELLED","DISPUTED")) {
            var e=execution(1);var d=piece(submit(e,"closed"));
            jdbc.update("update collaboration_collaboration set status=? where collaboration_id=?",status,e.collaboration());
            assertEquals(409,approve(e,d).status());
            assertEquals(409,submit(e,"new").status());
            assertTrue(collaboration(e,e.fixture().creator()).path("availableActions").isEmpty());
            assertEquals(200,call("GET","/deliverables/"+d.path("id").asText(),e.fixture().brand().token(),null).status());
        }
    }
}
