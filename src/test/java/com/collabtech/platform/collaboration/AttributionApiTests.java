package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.performance.application.services.AttributionService;
import com.collabtech.platform.performance.application.ports.AttributionPorts.*;
import com.collabtech.platform.performance.application.ports.PerformancePorts;
import com.collabtech.platform.performance.infrastructure.persistence.*;
import java.time.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class AttributionApiTests extends IncidentApiFixture {
    @Autowired AttributionService attribution;
    @Autowired AttributionPersistence attributionData;
    @Autowired PerformancePersistence performanceData;
    @Autowired PerformancePorts.Access performanceAccess;
    @Autowired PerformancePorts.UnitOfWork performanceWork;
    @Autowired Destinations destinations;
    @Autowired Identifiers identifiers;
    @Autowired Clock clock;
    Map<String,Object> createBody(String kind,UUID request){
        var body=new HashMap<String,Object>(Map.of("requestId",request,"kind",kind,"label","Visita o código de campaña"));
        if(kind.equals("LINK"))body.put("destinationId","demo");return body;
    }
    Result create(Execution e,String kind,UUID request)throws Exception{return call("POST","/collaborations/"+e.collaboration()+"/attribution",e.fixture().brand().token(),createBody(kind,request));}
    JsonNode mechanism(Execution e,String kind)throws Exception{var r=create(e,kind,UUID.randomUUID());assertEquals(201,r.status(),r.body().toString());return r.body().path("mechanism");}
    Result results(Execution e,User user,String query)throws Exception{return call("GET","/collaborations/"+e.collaboration()+"/attribution"+query,user.token(),null);}
    HttpResponse<String> visit(JsonNode m,String method,Map<String,String> headers)throws Exception{
        String path=URI.create(m.path("trackingUrl").asText()).getPath();
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(30));
        headers.forEach(request::header);
        return http.send(request.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
    }
    String evidence(Execution e)throws Exception {
        var now=Instant.now().minusSeconds(1);var r=call("POST","/collaborations/"+e.collaboration()+"/evidence",e.fixture().creator().token(),
                Map.of("requestId",UUID.randomUUID(),"description","Referencia declarada para consultas y uso de código",
                        "references",List.of(Map.of("url","https://example.com/proof")),"observedAt",now,
                        "period",Map.of("start",now.minusSeconds(3600),"end",now)));
        assertEquals(201,r.status(),r.body().toString());return r.body().path("evidence").path("id").asText();
    }
    Map<String,Object> event(String evidence,String type){
        return new HashMap<>(Map.of("requestId",UUID.randomUUID(),"eventId",UUID.randomUUID(),"type",type,"occurredAt",Instant.now(),"evidenceId",evidence));
    }
    Result declare(Execution e,JsonNode m,Object body)throws Exception{return call("POST","/attribution/"+m.path("id").asText()+"/interactions",e.fixture().brand().token(),body);}
    @Test void emptyResultsArePrivateExplicitEstimatesWithoutCreatingAReport()throws Exception{
        var e=execution(1);var r=results(e,e.fixture().creator(),"");
        assertEquals(200,r.status());assertEquals("NO_MECHANISM",r.body().path("availability").asText());
        assertTrue(r.body().path("isEstimate").asBoolean());assertFalse(r.body().path("verifiedSalesAvailable").asBoolean());
        assertFalse(r.body().path("uniquePeopleMeasured").asBoolean());assertTrue(r.body().path("warning").asText().contains("cautela"));
        assertEquals("FIRST_PARTY_TRACKING",r.body().path("sources").path("CLICK").asText());assertEquals("BRAND_DECLARATION",r.body().path("sources").path("CODE_REDEMPTION").asText());
        assertEquals(0,count("performance_report","collaboration_id",e.collaboration()));
        assertEquals(401,call("GET","/collaborations/"+e.collaboration()+"/attribution",null,null).status());
        assertEquals(403,results(e,user("brands"),"").status());assertEquals(403,results(e,user("creators"),"").status());
        assertEquals(400,results(e,e.fixture().brand(),"?page=-1").status());
        assertEquals(400,results(e,e.fixture().brand(),"?start=2026-01-01T00:00:00Z").status());
    }
    @Test void brandCreatesPinnedLinksAndCodesWithIdempotencyAndNoClientUrl()throws Exception{
        var e=execution(1);UUID request=UUID.randomUUID();var link=create(e,"LINK",request);
        assertEquals(201,link.status(),link.body().toString());var m=link.body().path("mechanism");
        assertEquals("https://example.com/collabpro-demo",m.path("destination").asText());assertEquals("ACTIVE",m.path("status").asText());
        assertEquals(m,create(e,"LINK",request).body().path("mechanism"));assertTrue(create(e,"LINK",request).body().path("replayed").asBoolean());
        assertEquals(409,create(e,"CODE",request).status());var code=mechanism(e,"CODE");
        assertTrue(code.path("code").asText().matches("CP-[A-Z2-9]{12}"));assertTrue(code.path("trackingUrl").isNull());
        assertEquals(2,results(e,e.fixture().creator(),"").body().path("mechanisms").path("total").asInt());
        String path="/collaborations/"+e.collaboration()+"/attribution";
        assertEquals(403,call("POST",path,e.fixture().creator().token(),createBody("LINK",UUID.randomUUID())).status());
        assertEquals(403,call("POST",path,user("brands").token(),createBody("LINK",UUID.randomUUID())).status());
        var body=createBody("LINK",UUID.randomUUID());body.put("destinationId","https://evil.example");
        assertEquals(400,call("POST",path,e.fixture().brand().token(),body).status());
        body=createBody("LINK",UUID.randomUUID());body.put("destination","https://evil.example");assertEquals(400,call("POST",path,e.fixture().brand().token(),body).status());
        assertEquals(401,call("GET","/attribution/destinations",null,null).status());
        var options=call("GET","/attribution/destinations",e.fixture().brand().token(),null);
        assertEquals(200,options.status());assertEquals("demo",options.body().get(0).path("id").asText());
    }
    @Test void publicClicksDeduplicateSessionAndRetryKeysAndIgnoreHeadAndPrefetch()throws Exception{
        var e=execution(1);var m=mechanism(e,"LINK");var first=visit(m,"GET",Map.of());
        assertEquals(302,first.statusCode(),first.body());assertEquals("https://example.com/collabpro-demo",first.headers().firstValue("Location").orElse(""));
        assertTrue(first.headers().firstValue("Cache-Control").orElse("").contains("no-store"));assertEquals("no-referrer",first.headers().firstValue("Referrer-Policy").orElse(""));
        String set=first.headers().firstValue("Set-Cookie").orElseThrow();assertTrue(set.contains("HttpOnly"));assertTrue(set.contains("Secure"));assertTrue(set.contains("SameSite=Lax"));
        var cookie=set.split(";",2)[0];assertEquals(302,visit(m,"GET",Map.of("Cookie",cookie)).statusCode());
        var key=UUID.randomUUID().toString();for(int i=0;i<2;i++)assertEquals(302,visit(m,"GET",Map.of("Idempotency-Key",key)).statusCode());
        assertEquals(302,visit(m,"HEAD",Map.of()).statusCode());assertEquals(302,visit(m,"GET",Map.of("Purpose","prefetch")).statusCode());
        assertEquals(302,visit(m,"GET",Map.of("Sec-Purpose","prefetch;prerender")).statusCode());
        var view=results(e,e.fixture().brand(),"").body();assertEquals(2,view.path("totals").path("clicks").asInt());
        var events=call("GET","/attribution/"+m.path("id").asText()+"/interactions",e.fixture().creator().token(),null);
        assertEquals(2,events.body().path("total").asInt());var item=events.body().path("items").get(0);
        assertEquals("FIRST_PARTY_TRACKING",item.path("source").asText());
        for(String field:List.of("eventKey","fingerprint","visitorKey","ipAddress","userAgent"))assertTrue(item.path(field).isMissingNode(),field);
    }
    @Test void concurrentPublicRetriesCountOnce()throws Exception{
        var e=execution(1);var m=mechanism(e,"LINK");var gate=new CountDownLatch(1);String key=UUID.randomUUID().toString();
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Integer> task=()->{gate.await(5,TimeUnit.SECONDS);return visit(m,"GET",Map.of("Idempotency-Key",key)).statusCode();};
            var a=pool.submit(task);var b=pool.submit(task);gate.countDown();assertEquals(302,a.get(20,TimeUnit.SECONDS));assertEquals(302,b.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,results(e,e.fixture().creator(),"").body().path("totals").path("clicks").asInt());
    }
    @Test void inactiveRemovedAndCancelledLinksCannotRedirectAndQueryCannotOverrideTarget()throws Exception{
        var e=execution(1);var m=mechanism(e,"LINK");String id=m.path("id").asText(),path=URI.create(m.path("trackingUrl").asText()).getPath();
        var override=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path+"?destination=https://evil.example")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(400,override.statusCode());assertTrue(override.headers().firstValue("Location").isEmpty());
        assertEquals(404,call("GET","/public/attribution/"+"A".repeat(43),null,null).status());
        String token=path.substring(path.lastIndexOf('/')+1);StringBuilder altered=new StringBuilder(token);
        for(int i=0;i<altered.length();i++)if(Character.isLetter(altered.charAt(i))){
            char current=altered.charAt(i);altered.setCharAt(i,Character.isUpperCase(current)?Character.toLowerCase(current):Character.toUpperCase(current));break;
        }
        assertNotEquals(token,altered.toString());
        assertEquals(404,call("GET","/public/attribution/"+altered,null,null).status());
        assertEquals(401,call("POST","/public/attribution/"+path.substring(path.lastIndexOf('/')+1),null,null).status());
        var removed=new com.collabtech.platform.performance.infrastructure.attribution.ConfiguredAttributionDestinations("","https://collabpro.example");
        var withoutDestination=new AttributionService(performanceAccess,performanceWork,performanceData,attributionData,attributionData,performanceData,removed,identifiers,clock);
        var failure=assertThrows(com.collabtech.platform.performance.domain.exceptions.PerformanceFailure.class,
                ()->withoutDestination.redirect(path.substring(path.lastIndexOf('/')+1),UUID.randomUUID().toString(),true));
        assertEquals(com.collabtech.platform.performance.domain.exceptions.PerformanceFailure.Code.ATTRIBUTION_INACTIVE,failure.code());
        var status=Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"status","INACTIVE");
        assertEquals(200,call("POST","/attribution/"+id+"/status",e.fixture().brand().token(),status).status());
        assertTrue(call("POST","/attribution/"+id+"/status",e.fixture().brand().token(),status).body().path("replayed").asBoolean());
        assertEquals(410,visit(m,"GET",Map.of()).statusCode());
        assertEquals(409,call("POST","/attribution/"+id+"/status",e.fixture().brand().token(),Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"status","ACTIVE")).status());
        assertEquals(200,call("POST","/attribution/"+id+"/status",e.fixture().brand().token(),Map.of("requestId",UUID.randomUUID(),"expectedVersion",1,"status","ACTIVE")).status());
        jdbc.update("update collaboration_collaboration set status='CANCELLED',cancelled_at=? where collaboration_id=?",java.sql.Timestamp.from(Instant.now()),e.collaboration());
        assertEquals(410,visit(m,"GET",Map.of()).statusCode());assertEquals(409,create(e,"CODE",UUID.randomUUID()).status());
        assertEquals(200,results(e,e.fixture().creator(),"").status());
        assertEquals("COLLABORATION_CANCELLED",results(e,e.fixture().creator(),"").body().path("mechanisms").path("items").get(0).path("mechanism").path("availability").asText());
    }
    @Test void manualDeclarationsNeedEvidenceAndStableEventIdsAcrossRetryRequests()throws Exception{
        var e=execution(1);var m=mechanism(e,"CODE");String proof=evidence(e);var body=event(proof,"CODE_REDEMPTION");var saved=declare(e,m,body);
        assertEquals(201,saved.status(),saved.body().toString());assertEquals("BRAND_DECLARATION",saved.body().path("interaction").path("source").asText());
        assertTrue(declare(e,m,body).body().path("replayed").asBoolean());String id=saved.body().path("interaction").path("id").asText();
        body.put("requestId",UUID.randomUUID());var retry=declare(e,m,body);assertEquals(201,retry.status());assertEquals(id,retry.body().path("interaction").path("id").asText());
        body.put("requestId",UUID.randomUUID());body.put("type","INQUIRY");assertEquals(409,declare(e,m,body).status());
        body.put("requestId",UUID.randomUUID());body.put("type","CODE_REDEMPTION");assertEquals(409,declare(e,mechanism(e,"CODE"),body).status());
        var view=results(e,e.fixture().brand(),"").body();assertEquals(1,view.path("totals").path("codeRedemptions").asInt());assertEquals(0,view.path("totals").path("clicks").asInt());
        assertFalse(view.path("verifiedSalesAvailable").asBoolean());assertTrue(view.path("warning").asText().contains("ventas verificadas"));
    }
    @Test void foreignSourcesWrongRolesForgedClicksAndInvalidTimesAreRejected()throws Exception{
        var e=execution(1);var other=execution(1);var m=mechanism(e,"CODE");String proof=evidence(e);String path="/attribution/"+m.path("id").asText()+"/interactions";
        assertEquals(403,call("POST",path,e.fixture().creator().token(),event(proof,"INQUIRY")).status());
        assertEquals(403,call("POST",path,other.fixture().brand().token(),event(proof,"INQUIRY")).status());
        assertEquals(403,call("GET",path,other.fixture().creator().token(),null).status());assertEquals(401,call("POST",path,null,event(proof,"INQUIRY")).status());
        assertEquals(409,declare(e,m,event(evidence(other),"INQUIRY")).status());assertEquals(409,declare(e,m,event(UUID.randomUUID().toString(),"INQUIRY")).status());
        assertEquals(400,declare(e,m,event(proof,"CLICK")).status());assertEquals(400,declare(e,mechanism(e,"LINK"),event(proof,"CODE_REDEMPTION")).status());
        var body=event(proof,"INQUIRY");body.put("occurredAt",Instant.now().plusSeconds(3600));assertEquals(400,declare(e,m,body).status());
        body=event(proof,"INQUIRY");body.put("occurredAt",Instant.parse(m.path("createdAt").asText()).minusSeconds(1));assertEquals(400,declare(e,m,body).status());
        body=event(proof,"INQUIRY");body.put("source","FIRST_PARTY_TRACKING");assertEquals(400,declare(e,m,body).status());
    }
    @Test void evidenceRejectionExcludesDeclaredCountsWhilePreservingAudit()throws Exception{
        var e=execution(1);var m=mechanism(e,"CODE");String proof=evidence(e);
        assertEquals(201,declare(e,m,event(proof,"INQUIRY")).status());assertEquals(1,results(e,e.fixture().brand(),"").body().path("totals").path("inquiries").asInt());
        assertEquals(200,call("POST","/performance-evidence/"+proof+"/verification",e.fixture().brand().token(),
                Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"decision","REJECTED","observations","La referencia no permite corroborar la consulta.")).status());
        assertEquals(0,results(e,e.fixture().brand(),"").body().path("totals").path("inquiries").asInt());
        var events=call("GET","/attribution/"+m.path("id").asText()+"/interactions",e.fixture().creator().token(),null);
        assertEquals(1,events.body().path("total").asInt());assertFalse(events.body().path("items").get(0).path("includedInTotals").asBoolean());
        assertEquals("REJECTED",events.body().path("items").get(0).path("evidenceStatus").asText());
    }
    @Test void periodBoundariesAreExclusiveAndTotalsAreIndependentOfPageSize()throws Exception{
        var e=execution(1);var first=mechanism(e,"CODE");var second=mechanism(e,"CODE");String proof=evidence(e);
        var body=event(proof,"INQUIRY");Instant boundary=Instant.parse(second.path("createdAt").asText());body.put("occurredAt",boundary);
        assertEquals(201,declare(e,first,body).status());assertEquals(201,declare(e,second,event(proof,"CODE_REDEMPTION")).status());
        var all=results(e,e.fixture().creator(),"?size=1").body();assertEquals(2,all.path("mechanisms").path("total").asInt());
        assertEquals(1,all.path("mechanisms").path("items").size());assertEquals(1,all.path("totals").path("inquiries").asInt());assertEquals(1,all.path("totals").path("codeRedemptions").asInt());
        var before=results(e,e.fixture().brand(),"?start="+boundary.minusSeconds(3600)+"&end="+boundary).body();
        assertEquals(0,before.path("totals").path("inquiries").asInt());
        var includes=results(e,e.fixture().brand(),"?start="+boundary+"&end="+Instant.now()).body();assertEquals(1,includes.path("totals").path("inquiries").asInt());
        assertEquals(400,results(e,e.fixture().brand(),"?start="+Instant.now()+"&end="+boundary).status());
    }
    @Test void statusChangesAreVersionedAndDoNotLoseConcurrentUpdates()throws Exception{
        var e=execution(1);var m=mechanism(e,"CODE");var gate=new CountDownLatch(1);String path="/attribution/"+m.path("id").asText()+"/status";
        assertEquals(403,call("POST",path,e.fixture().creator().token(),Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"status","INACTIVE")).status());
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Result> task=()->{gate.await(5,TimeUnit.SECONDS);return call("POST",path,e.fixture().brand().token(),Map.of("requestId",UUID.randomUUID(),"expectedVersion",0,"status","INACTIVE"));};
            var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
            assertEquals(List.of(200,409),java.util.stream.Stream.of(a.get(20,TimeUnit.SECONDS).status(),b.get(20,TimeUnit.SECONDS).status()).sorted().toList());
        }
    }
    @Test void failingCommandReceiptRollsBackBothMechanismAndDeclaredInteraction()throws Exception{
        var e=execution(1);var broken=new PerformancePorts.Commands(){
            public Optional<PerformancePorts.Command> command(UUID r,UUID a,UUID q){return performanceData.command(r,a,q);}
            public String fingerprint(Object value){return performanceData.fingerprint(value);}
            public void command(UUID r,UUID a,UUID q,PerformancePorts.Command command,Instant now){throw new IllegalStateException("Receipt failed");}
        };
        var service=new AttributionService(performanceAccess,performanceWork,performanceData,attributionData,attributionData,broken,destinations,identifiers,clock);
        var account=UUID.fromString(e.fixture().brand().account());var collaboration=UUID.fromString(e.collaboration());
        assertThrows(IllegalStateException.class,()->service.create(account,collaboration,new AttributionService.Create(UUID.randomUUID(),
                com.collabtech.platform.performance.domain.model.AttributionModels.Kind.CODE,"Rollback",null)));
        var report=performanceData.report(collaboration,false).orElseThrow();assertEquals(0,count("performance_attribution","report_id",report.id().toString()));
        var m=mechanism(e,"CODE");String proof=evidence(e);
        assertThrows(IllegalStateException.class,()->service.record(account,UUID.fromString(m.path("id").asText()),new AttributionService.RecordInteraction(
                UUID.randomUUID(),UUID.randomUUID(),com.collabtech.platform.performance.domain.model.AttributionModels.InteractionType.INQUIRY,Instant.now(),UUID.fromString(proof))));
        assertEquals(0,count("performance_interaction","report_id",report.id().toString()));
    }
    @Test void fullNonFinancialJourneyPreservesOriginalTermsAndReachesResultsForBothRoles()throws Exception{
        var e=execution(1);var first=piece(submit(e,"journey-original"));var rejected=reject(e,first,Instant.now().plusSeconds(3600));
        var correction=call("POST","/deliverables/"+first.path("id").asText()+"/versions",e.fixture().creator().token(),
                Map.of("requestId",UUID.randomUUID(),"expectedVersion",rejected.path("version").asLong(),"content",content("journey-correction")));
        var corrected=piece(correction);assertEquals(200,approve(e,corrected).status());
        var completed=collaboration(e,e.fixture().brand());assertEquals("COMPLETED",completed.path("status").asText());
        var incident=openIncident(e,e.fixture().creator(),"Confirmar el cierre del acuerdo");resolveKeep(e,incident,e.fixture().brand(),e.fixture().creator());
        var code=mechanism(e,"CODE");var link=mechanism(e,"LINK");String proof=evidence(e);
        assertEquals(201,declare(e,code,event(proof,"CODE_REDEMPTION")).status());assertEquals(302,visit(link,"GET",Map.of()).statusCode());
        for(var user:List.of(e.fixture().brand(),e.fixture().creator())){
            var view=results(e,user,"");assertEquals(200,view.status());assertEquals(1,view.body().path("totals").path("clicks").asInt());assertEquals(1,view.body().path("totals").path("codeRedemptions").asInt());
            var metrics=call("GET","/collaborations/"+e.collaboration()+"/metrics",user.token(),null);
            assertEquals("NO_DATA",metrics.body().path("automaticAvailability").asText());assertEquals(1,metrics.body().path("manualEvidenceCount").asInt());
            var history=call("GET","/collaborations/history/"+e.collaboration(),user.token(),null);assertEquals(200,history.status());
            assertEquals("AVAILABLE",history.body().path("references").path("performance").path("availability").asText());
            assertEquals("NOT_AVAILABLE",history.body().path("references").path("compensation").path("availability").asText());
            assertEquals(completed.path("snapshot"),history.body().path("collaboration").path("snapshot"));
            assertEquals(completed.path("completedAt"),history.body().path("collaboration").path("completedAt"));
        }
    }
}
