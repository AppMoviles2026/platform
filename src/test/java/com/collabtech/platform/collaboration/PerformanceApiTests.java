package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.performance.application.ports.PerformancePorts.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import java.time.Instant;
import java.time.Duration;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

@Import(PerformanceApiTests.Configuration.class)
class PerformanceApiTests extends DeliveryApiFixture {
    static final String POST="1234567890123456789";
    @TestConfiguration(proxyBeanMethods=false) static class Configuration {
        @Bean @Primary Stub metricsStub(){return new Stub();}
    }
    static class Stub implements SocialMetricsProvider {
        final Map<UUID,FetchStatus> statuses=new ConcurrentHashMap<>();
        final Map<UUID,Long> views=new ConcurrentHashMap<>();
        final Map<UUID,AtomicInteger> calls=new ConcurrentHashMap<>();
        public FetchResult fetch(UUID creator,UUID social,String platform,String post) {
            calls.computeIfAbsent(social,k->new AtomicInteger()).incrementAndGet();
            var status=statuses.getOrDefault(social,FetchStatus.AVAILABLE);
            if(status!=FetchStatus.AVAILABLE)return FetchResult.unavailable(status,platform);
            var now=Instant.now().minusSeconds(1);
            return new FetchResult(status,platform,new Period(now.minusSeconds(3600),now),
                    Map.of(MetricType.VIEWS,views.getOrDefault(social,100L),MetricType.LIKES,0L));
        }
        int calls(UUID social){return calls.getOrDefault(social,new AtomicInteger()).get();}
    }
    @Autowired Stub provider;
    @Autowired com.collabtech.platform.performance.application.ports.PerformancePorts.Access performanceAccess;
    @Autowired com.collabtech.platform.performance.application.ports.PerformancePorts.UnitOfWork performanceWork;
    @Autowired com.collabtech.platform.performance.infrastructure.persistence.PerformancePersistence performanceData;
    @Autowired java.time.Clock performanceClock;
    com.collabtech.platform.performance.application.services.PerformanceService performanceAt(java.time.Clock clock,Commands commands){
        return new com.collabtech.platform.performance.application.services.PerformanceService(performanceAccess,performanceWork,
                performanceData,performanceData,commands,performanceData,provider,clock);
    }
    @Test void failedCommandReceiptRollsBackCapturesBindingsAndAttempts()throws Exception{
        var p=published(1);var request=UUID.randomUUID();
        var broken=new Commands(){
            public Optional<Command> command(UUID r,UUID a,UUID q){return performanceData.command(r,a,q);}
            public String fingerprint(Object input){return performanceData.fingerprint(input);}
            public void command(UUID r,UUID a,UUID q,Command command,Instant now){throw new IllegalStateException("Simulated receipt failure");}
        };
        var input=new com.collabtech.platform.performance.application.services.PerformanceService.RefreshInput(request,p.social(),UUID.fromString(p.submission()),POST);
        assertThrows(IllegalStateException.class,()->performanceAt(performanceClock,broken).refresh(
                UUID.fromString(p.execution().fixture().brand().account()),UUID.fromString(p.execution().collaboration()),input));
        String reportId=jdbc.queryForObject("select report_id from performance_report where collaboration_id=?",String.class,p.execution().collaboration());
        assertEquals(0,count("performance_publication","report_id",reportId));assertEquals(0,count("performance_command","report_id",reportId));
        assertEquals(200,refresh(p,request).status());assertEquals(2,provider.calls(p.social()));
    }
    @Test void sequencePreservesDifferentMeasurementsWithIdenticalCaptureTimestamp()throws Exception{
        var p=published(1);var fixed=java.time.Clock.fixed(Instant.now().plusSeconds(60),java.time.ZoneOffset.UTC);
        var service=performanceAt(fixed,performanceData);
        var account=UUID.fromString(p.execution().fixture().brand().account());var collaboration=UUID.fromString(p.execution().collaboration());
        var first=service.refresh(account,collaboration,new com.collabtech.platform.performance.application.services.PerformanceService.RefreshInput(UUID.randomUUID(),p.social(),UUID.fromString(p.submission()),POST));
        provider.views.put(p.social(),250L);
        var second=service.refresh(account,collaboration,new com.collabtech.platform.performance.application.services.PerformanceService.RefreshInput(UUID.randomUUID(),p.social(),UUID.fromString(p.submission()),POST));
        assertEquals(first.snapshot().capturedAt(),second.snapshot().capturedAt());assertNotEquals(first.snapshot().id(),second.snapshot().id());
        assertEquals(250,report(p.execution(),p.execution().fixture().brand()).body().path("publications").path("items").get(0).path("snapshot").path("metrics").path("VIEWS").asInt());
    }
    UUID social(User creator) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into identity_social_account(social_id,profile_id,platform,external_account_id,username,status) values(?,?,'tiktok',?,?,'ACTIVE')",
                id.toString(),creator.profile(),UUID.randomUUID().toString(),"test-creator");
        return id;
    }
    record Published(Execution execution,String deliverable,String submission,UUID social){}
    Published published(int quantity)throws Exception {
        var e=execution(quantity);
        var body=Map.of("requestId",UUID.randomUUID(),"obligationId",e.obligation(),"content",
                Map.of("content",Map.of("url","https://www.tiktok.com/@test/video/"+POST),
                        "evidence",List.of(Map.of("url","https://example.com/publication-proof"))));
        var d=piece(call("POST","/collaborations/"+e.collaboration()+"/deliverables",e.fixture().creator().token(),body));
        return new Published(e,d.path("id").asText(),d.path("currentSubmission").path("id").asText(),social(e.fixture().creator()));
    }
    Map<String,Object> refreshBody(Published p,UUID request) {
        return new HashMap<>(Map.of("requestId",request,"socialId",p.social(),"submissionId",p.submission(),"postId",POST));
    }
    Result refresh(Published p,UUID request)throws Exception {
        return call("POST","/collaborations/"+p.execution().collaboration()+"/metrics/refresh",p.execution().fixture().brand().token(),refreshBody(p,request));
    }
    Result report(Execution e,User actor)throws Exception{return call("GET","/collaborations/"+e.collaboration()+"/metrics",actor.token(),null);}
    Map<String,Object> evidenceBody() {
        var now=Instant.now().minusSeconds(5);
        return new HashMap<>(Map.of("requestId",UUID.randomUUID(),"description","Captura de estadísticas declarada por el creador",
                "references",List.of(Map.of("url","https://example.com/manual-proof")),"claimedMetrics",Map.of("VIEWS",125),
                "observedAt",now,"period",Map.of("start",now.minusSeconds(86400),"end",now)));
    }
    Result evidence(Execution e,Object body)throws Exception{return call("POST","/collaborations/"+e.collaboration()+"/evidence",e.fixture().creator().token(),body);}
    @Test void noDataIsExplicitAndReadsDoNotCreateReportsOrCallProviders()throws Exception {
        var e=execution(1);
        var result=report(e,e.fixture().creator());assertEquals(200,result.status(),result.body().toString());
        assertEquals("NO_DATA",result.body().path("automaticAvailability").asText());
        assertEquals(0,result.body().path("availablePublications").asLong());
        assertFalse(result.body().path("manualEvidenceIsAutomatic").asBoolean());
        assertEquals(0,count("performance_report","collaboration_id",e.collaboration()));
        assertEquals(401,call("GET","/collaborations/"+e.collaboration()+"/metrics",null,null).status());
        assertEquals(403,report(e,user("brands")).status());
        assertEquals(404,call("GET","/collaborations/"+UUID.randomUUID()+"/metrics",e.fixture().brand().token(),null).status());
        assertEquals(400,call("GET","/collaborations/"+e.collaboration()+"/metrics?page=-1",e.fixture().brand().token(),null).status());
    }
    @Test void capturesKeepSourcePeriodAndMissingValuesWithoutAddingLifetimeSnapshots()throws Exception {
        var p=published(2);var first=refresh(p,UUID.randomUUID());
        assertEquals(200,first.status(),first.body().toString());
        var snapshot=first.body().path("snapshot");assertEquals("AUTOMATED",snapshot.path("sourceType").asText());
        assertEquals("tiktok",snapshot.path("provider").asText());assertEquals("LIFETIME",snapshot.path("periodKind").asText());
        assertFalse(snapshot.path("period").path("start").isMissingNode());assertEquals(100,snapshot.path("metrics").path("VIEWS").asInt());
        assertTrue(snapshot.path("metrics").path("REACH").isMissingNode());
        provider.views.put(p.social(),125L);assertEquals(200,refresh(p,UUID.randomUUID()).status());
        var report=report(p.execution(),p.execution().fixture().creator()).body();
        assertEquals(1,report.path("availablePublications").asInt());
        assertEquals(125,report.path("publications").path("items").get(0).path("snapshot").path("metrics").path("VIEWS").asInt());
        var history=call("GET","/collaborations/"+p.execution().collaboration()+"/metrics/snapshots?size=1",p.execution().fixture().brand().token(),null);
        assertEquals(200,history.status());assertEquals(2,history.body().path("total").asInt());assertEquals(1,history.body().path("items").size());
        assertEquals(2,provider.calls(p.social()));
    }
    @Test void concurrentRefreshReplaysOneProviderRequestAndRejectsKeyReuse()throws Exception {
        var p=published(1);var request=UUID.randomUUID();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Result> task=()->{gate.await(5,TimeUnit.SECONDS);return refresh(p,request);};
            var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
            var r1=a.get(25,TimeUnit.SECONDS);var r2=b.get(25,TimeUnit.SECONDS);
            assertEquals(200,r1.status(),r1.body().toString());assertEquals(200,r2.status(),r2.body().toString());
            assertEquals(r1.body().path("refresh").path("id"),r2.body().path("refresh").path("id"));
            assertNotEquals(r1.body().path("replayed"),r2.body().path("replayed"));assertEquals(1,provider.calls(p.social()));
        }
        var changed=refreshBody(p,request);changed.put("postId","999");
        assertEquals(409,call("POST","/collaborations/"+p.execution().collaboration()+"/metrics/refresh",p.execution().fixture().brand().token(),changed).status());
    }
    @Test void providerFailureKeepsLastSnapshotAndIsExplicitAndIdempotent()throws Exception {
        var p=published(1);assertEquals(200,refresh(p,UUID.randomUUID()).status());
        for(var status:List.of(FetchStatus.AUTHORIZATION_REQUIRED,FetchStatus.INSUFFICIENT_SCOPE,FetchStatus.RATE_LIMITED,FetchStatus.PROVIDER_UNAVAILABLE)) {
            provider.statuses.put(p.social(),status);var request=UUID.randomUUID();var r=refresh(p,request);
            assertEquals(200,r.status());assertEquals(status.name(),r.body().path("refresh").path("status").asText());
            assertTrue(r.body().path("snapshot").isNull());assertTrue(refresh(p,request).body().path("replayed").asBoolean());
            var view=report(p.execution(),p.execution().fixture().brand()).body().path("publications").path("items").get(0);
            assertEquals("AVAILABLE_STALE",view.path("availability").asText());assertEquals(100,view.path("snapshot").path("metrics").path("VIEWS").asInt());
        }
        assertEquals(5,provider.calls(p.social()));
        assertEquals(1,call("GET","/collaborations/"+p.execution().collaboration()+"/metrics/snapshots",p.execution().fixture().brand().token(),null).body().path("total").asInt());
    }
    @Test void foreignSourcesSocialAccountsAndMismatchedPostsNeverReachProvider()throws Exception {
        var p=published(1);var other=published(1);var body=refreshBody(p,UUID.randomUUID());String path="/collaborations/"+p.execution().collaboration()+"/metrics/refresh";
        body.put("socialId",other.social());assertEquals(403,call("POST",path,p.execution().fixture().brand().token(),body).status());
        body.put("socialId",p.social());body.put("submissionId",other.submission());assertEquals(404,call("POST",path,p.execution().fixture().brand().token(),body).status());
        body.put("submissionId",p.submission());body.put("postId","999");assertEquals(409,call("POST",path,p.execution().fixture().brand().token(),body).status());
        assertEquals(403,call("POST",path,user("creators").token(),refreshBody(p,UUID.randomUUID())).status());assertEquals(0,provider.calls(p.social()));
    }
    @Test void rejectedAndReplacedContentIsHistoricalAndCanBeReboundWithoutDoubleCounting()throws Exception {
        var p=published(1);assertEquals(200,refresh(p,UUID.randomUUID()).status());
        var d=call("GET","/deliverables/"+p.deliverable(),p.execution().fixture().brand().token(),null).body();
        var rejected=reject(p.execution(),d,Instant.now().plusSeconds(3600));
        assertEquals(409,refresh(p,UUID.randomUUID()).status());var view=report(p.execution(),p.execution().fixture().brand()).body();
        assertEquals("NO_DATA",view.path("automaticAvailability").asText());assertEquals("HISTORICAL_ONLY",view.path("publications").path("items").get(0).path("availability").asText());
        var fixed=call("POST","/deliverables/"+p.deliverable()+"/versions",p.execution().fixture().creator().token(),
                Map.of("requestId",UUID.randomUUID(),"expectedVersion",rejected.path("version").asLong(),"content",
                        Map.of("content",Map.of("url","https://www.tiktok.com/@test/video/"+POST),"evidence",List.of(Map.of("url","https://example.com/correction")))));
        var current=piece(fixed).path("currentSubmission").path("id").asText();
        var corrected=new Published(p.execution(),p.deliverable(),current,p.social());assertEquals(200,refresh(corrected,UUID.randomUUID()).status());
        view=report(p.execution(),p.execution().fixture().brand()).body();assertEquals(1,view.path("availablePublications").asInt());
        assertEquals(1,view.path("publications").path("total").asInt());assertEquals(2,view.path("publications").path("items").get(0).path("bindings").size());
    }
    @Test void manualEvidenceIsSeparateAndReviewCannotMakeItAutomatic()throws Exception {
        var e=execution(1);var body=evidenceBody();var saved=evidence(e,body);
        assertEquals(201,saved.status(),saved.body().toString());String id=saved.body().path("evidence").path("id").asText();
        var replay=evidence(e,body);assertEquals(id,replay.body().path("evidence").path("id").asText());assertTrue(replay.body().path("replayed").asBoolean());
        var review=Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"decision","VERIFIED");
        assertEquals(403,call("POST","/performance-evidence/"+id+"/verification",e.fixture().creator().token(),review).status());
        assertEquals(403,call("GET","/performance-evidence/"+id,user("brands").token(),null).status());
        var reviewed=call("POST","/performance-evidence/"+id+"/verification",e.fixture().brand().token(),review);
        assertEquals(200,reviewed.status(),reviewed.body().toString());assertEquals("VERIFIED",reviewed.body().path("evidence").path("status").asText());
        assertEquals("MANUAL",reviewed.body().path("evidence").path("sourceType").asText());assertEquals(1,reviewed.body().path("evidence").path("version").asInt());
        assertTrue(call("POST","/performance-evidence/"+id+"/verification",e.fixture().brand().token(),review).body().path("replayed").asBoolean());
        assertEquals(409,call("POST","/performance-evidence/"+id+"/verification",e.fixture().brand().token(),
                Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"decision","REJECTED","observations","Otra decisión")).status());
        var report=report(e,e.fixture().brand()).body();assertEquals("NO_DATA",report.path("automaticAvailability").asText());assertEquals(1,report.path("manualEvidenceCount").asInt());
        var list=call("GET","/collaborations/"+e.collaboration()+"/evidence?status=VERIFIED&size=1",e.fixture().creator().token(),null);
        assertEquals(1,list.body().path("total").asInt());assertEquals(id,list.body().path("items").get(0).path("id").asText());
        assertEquals(200,call("GET","/performance-evidence/"+id,e.fixture().creator().token(),null).status());
        var history=call("GET","/collaborations/history/"+e.collaboration(),e.fixture().brand().token(),null);
        assertEquals("AVAILABLE",history.body().path("references").path("performance").path("availability").asText());
        assertEquals("NOT_AVAILABLE",history.body().path("references").path("compensation").path("availability").asText());
    }
    @Test void rejectsInvalidOrForgedEvidenceWithoutPersistingIt()throws Exception {
        var e=execution(1);String path="/collaborations/"+e.collaboration()+"/evidence";
        assertEquals(403,call("POST",path,e.fixture().brand().token(),evidenceBody()).status());
        for(Object invalid:List.of(Map.of("VIEWS",-1),Map.of("VIEWS",1.5),Map.of("VIEWS","9223372036854775808"))) {
            var body=evidenceBody();body.put("claimedMetrics",invalid);assertEquals(400,evidence(e,body).status());
        }
        for(String field:List.of("sourceType","demonstration","status")) {
            var body=evidenceBody();body.put(field,field.equals("demonstration")?true:"AUTOMATED");assertEquals(400,evidence(e,body).status());
        }
        var body=evidenceBody();body.put("observedAt",Instant.now().plusSeconds(3600));assertEquals(400,evidence(e,body).status());
        body=evidenceBody();body.put("references",List.of(Map.of("url","http://example.com/proof")));assertEquals(400,evidence(e,body).status());
        body=evidenceBody();body.put("references",List.of());assertEquals(400,evidence(e,body).status());
        body=evidenceBody();body.put("submissionId",UUID.randomUUID());assertEquals(404,evidence(e,body).status());
        assertEquals(0,report(e,e.fixture().creator()).body().path("manualEvidenceCount").asInt());
    }
    @Test void rejectionRequiresObservationsAndDifferentEvidenceKeysCannotOverwriteReview()throws Exception {
        var e=execution(1);var body=evidenceBody();var saved=evidence(e,body);
        String id=saved.body().path("evidence").path("id").asText();
        assertEquals(400,call("POST","/performance-evidence/"+id+"/verification",e.fixture().brand().token(),
                Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"decision","REJECTED")).status());
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Result> task=()->{gate.await(5,TimeUnit.SECONDS);return call("POST","/performance-evidence/"+id+"/verification",e.fixture().brand().token(),
                    Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"decision","REJECTED","observations","No se distingue la fecha del periodo."));};
            var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
            assertEquals(List.of(200,409),java.util.stream.Stream.of(a.get(25,TimeUnit.SECONDS).status(),b.get(25,TimeUnit.SECONDS).status()).sorted().toList());
        }
        body.put("description","Solicitud diferente");assertEquals(409,evidence(e,body).status());
    }
    Result uploadEvidence(Execution e,UUID request,String filename,String media,byte[] bytes)throws Exception {
        String boundary="boundary"+UUID.randomUUID();
        var prefix=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: "+media+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        var suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/collaborations/"+e.collaboration()+"/evidence-assets"))
                .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+e.fixture().creator().token())
                .header("Content-Type","multipart/form-data; boundary="+boundary);
        if(request!=null)req.header("Idempotency-Key",request.toString());
        var response=http.send(req.POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),
                HttpRequest.BodyPublishers.ofByteArray(bytes),HttpRequest.BodyPublishers.ofByteArray(suffix))).build(),HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(),json.readTree(response.body()));
    }
    @Test void filesArePrivateUntilReferencedAndNeverReusableByAnotherCollaboration()throws Exception {
        var e=execution(1);var request=UUID.randomUUID();byte[] bytes="%PDF-1.7\nEvidence".getBytes(StandardCharsets.US_ASCII);
        var upload=uploadEvidence(e,request,"insights.pdf","application/pdf",bytes);assertEquals(201,upload.status(),upload.body().toString());
        String asset=upload.body().path("asset").path("id").asText();
        assertEquals(asset,uploadEvidence(e,request,"insights.pdf","application/pdf",bytes).body().path("asset").path("id").asText());
        assertEquals(200,call("GET","/performance-assets/"+asset,e.fixture().creator().token(),null).status());
        assertEquals(403,call("GET","/performance-assets/"+asset,e.fixture().brand().token(),null).status());
        var other=execution(1);var foreign=evidenceBody();foreign.put("references",List.of(Map.of("assetId",asset)));assertEquals(403,evidence(other,foreign).status());
        var body=evidenceBody();body.put("references",List.of(Map.of("assetId",asset)));assertEquals(201,evidence(e,body).status());
        assertEquals(200,call("GET","/performance-assets/"+asset,e.fixture().brand().token(),null).status());
        assertEquals(403,call("GET","/performance-assets/"+asset,other.fixture().creator().token(),null).status());
        var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/performance-assets/"+asset+"/content"))
                .header("Authorization","Bearer "+e.fixture().brand().token()).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200,response.statusCode());assertArrayEquals(bytes,response.body());assertEquals("nosniff",response.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals(415,uploadEvidence(e,UUID.randomUUID(),"fake.png","image/png",bytes).status());
        assertEquals(400,uploadEvidence(e,null,"insights.pdf","application/pdf",bytes).status());
        assertEquals(413,uploadEvidence(e,UUID.randomUUID(),"large.pdf","application/pdf",new byte[10485761]).status());
    }
    @Test void completedAndDisputedCollaborationsKeepMetricsWithoutChangingTheirStateButCancellationBlocksWrites()throws Exception {
        var p=published(1);var e=p.execution();
        var d=call("GET","/deliverables/"+p.deliverable(),e.fixture().brand().token(),null).body();
        assertEquals(200,approve(e,d).status());assertEquals("COMPLETED",collaboration(e,e.fixture().brand()).path("status").asText());
        var request=UUID.randomUUID();assertEquals(200,refresh(p,request).status());assertEquals(201,evidence(e,evidenceBody()).status());
        assertEquals("COMPLETED",collaboration(e,e.fixture().brand()).path("status").asText());
        jdbc.update("update collaboration_collaboration set status='DISPUTED',open_incident_count=1 where collaboration_id=?",e.collaboration());
        assertEquals(200,refresh(p,UUID.randomUUID()).status());assertEquals(201,evidence(e,evidenceBody()).status());
        assertEquals(1,jdbc.queryForObject("select open_incident_count from collaboration_collaboration where collaboration_id=?",Integer.class,e.collaboration()));
        jdbc.update("update collaboration_collaboration set status='CANCELLED',cancelled_at=? where collaboration_id=?",java.sql.Timestamp.from(Instant.now()),e.collaboration());
        assertEquals(409,refresh(p,UUID.randomUUID()).status());assertEquals(409,evidence(e,evidenceBody()).status());
        assertEquals(200,refresh(p,request).status());assertEquals(200,report(e,e.fixture().creator()).status());
    }
}
