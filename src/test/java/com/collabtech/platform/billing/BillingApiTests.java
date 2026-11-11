package com.collabtech.platform.billing;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.billing.application.services.BillingService;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.domain.model.BillingModels.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("test")
@org.springframework.context.annotation.Import(BillingApiTests.Fakes.class)
class BillingApiTests {
    @TestConfiguration static class Fakes {
        @Bean @Primary FakeGateway controlledBillingGateway(){return new FakeGateway();}
    }
    static class FakeGateway implements Gateway {
        final ConcurrentHashMap<UUID,String> states=new ConcurrentHashMap<>();final ConcurrentHashMap<UUID,String> payers=new ConcurrentHashMap<>();
        final ConcurrentHashMap<String,AtomicInteger> exchanges=new ConcurrentHashMap<>();final Set<String> failIdentity=ConcurrentHashMap.newKeySet(),uncertainExchange=ConcurrentHashMap.newKeySet();
        final Set<UUID> failPrepare=ConcurrentHashMap.newKeySet(),pending=ConcurrentHashMap.newKeySet();final AtomicInteger preparations=new AtomicInteger(),completions=new AtomicInteger();
        final ThreadLocal<UUID> identityAuthorization=new ThreadLocal<>();
        public Configuration configuration(){return new Configuration(true,true,true,"SANDBOX","https://example.test/callback","https://example.test/identity","collabpro://billing-return",1800);}
        public Preparation prepare(UUID id,UUID account,String state){states.put(id,state);preparations.incrementAndGet();
            if(failPrepare.remove(account))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);
            return new Preparation("SETUP-"+id,"CUSTOMER-"+account,"https://www.sandbox.paypal.com/approve?state="+state);}
        public String identityApproval(String state){return "https://www.sandbox.paypal.com/connect?state="+state;}
        public Verified completeFunding(UUID id,UUID account,String setup,String customer){completions.incrementAndGet();
            if(pending.contains(id))throw new BillingFailure(Code.AWAITING_APPROVAL,true);
            return new Verified("VAULT-"+id,payers.getOrDefault(id,payer(id)),"business@example.com");}
        static String payer(UUID id){return id.toString().replace("-","").substring(0,13).toUpperCase(Locale.ROOT);}
        public Token exchangeIdentity(String code){exchanges.computeIfAbsent(code,k->new AtomicInteger()).incrementAndGet();if(uncertainExchange.remove(code))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);return new Token("ACCESS-"+code,Instant.now().plusSeconds(3600));}
        public Verified identity(String access){String code=access.substring(7);if(failIdentity.remove(code))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);
            return new Verified(payer(UUID.nameUUIDFromBytes(code.getBytes(java.nio.charset.StandardCharsets.UTF_8))),payer(UUID.nameUUIDFromBytes(code.getBytes(java.nio.charset.StandardCharsets.UTF_8))),"creator@example.com");}
        public boolean verify(Signature signature,Map<String,Object> event){return signature!=null&&"valid".equals(signature.signature());}
    }
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired FakeGateway provider;@Autowired BillingService service;@LocalServerPort int port;
    final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    record User(String token,String account){}record Result(int status,JsonNode body,String location){}
    Result call(String method,String path,String token,Object body)throws Exception{return call(method,path,token,body,null);}
    Result call(String method,String path,String token,Object body,String signature)throws Exception{
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(30));
        if(token!=null)req.header("Authorization","Bearer "+token);if(body!=null)req.header("Content-Type","application/json");
        if(signature!=null)req.header("PAYPAL-TRANSMISSION-SIG",signature);
        req.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var r=http.send(req.build(),HttpResponse.BodyHandlers.ofString());
        return new Result(r.statusCode(),r.body().isBlank()?json.createObjectNode():json.readTree(r.body()),r.headers().firstValue("Location").orElse(null));
    }
    User user(String role)throws Exception{String email="billing-"+UUID.randomUUID()+"@example.com",password="BillingDemo123!";
        assertEquals(201,call("POST","/auth/"+role,null,Map.of(role.equals("brands")?"businessName":"displayName","Billing","email",email,"password",password)).status());
        var result=call("POST","/auth/sessions",null,Map.of("email",email,"password",password));assertEquals(200,result.status());
        return new User(result.body().path("accessToken").asText(),result.body().path("account").path("accountId").asText());}
    Result begin(User u,UUID request,String client)throws Exception{return call("POST","/user/"+u.account()+"/billing",u.token(),Map.of("requestId",request,"client",client));}
    String id(Result r){assertEquals(202,r.status(),r.body().toString());return r.body().path("authorizationId").asText();}
    String state(Result r){return URLDecoder.decode(r.body().path("approvalUrl").asText().split("state=")[1],java.nio.charset.StandardCharsets.UTF_8);}
    Result callback(Result r,boolean creator,String code)throws Exception{return call("GET","/billing/paypal/"+(creator?"identity-callback":"callback")+"?state="+state(r)+(creator?"&code="+code:""),null,null);}
    Map<String,Object> event(String id,String type,String resource){return Map.of("id",id,"event_type",type,"resource",Map.of("id",resource));}
    Result webhook(Map<String,Object> event,String signature)throws Exception{return call("POST","/webhooks/payments",null,event,signature);}
    String firstMethod(User u)throws Exception{var list=call("GET","/user/"+u.account()+"/billing",u.token(),null);assertEquals(200,list.status());return list.body().path("items").get(0).path("methodId").asText();}

    @ParameterizedTest @ValueSource(strings={"brands","creators"})
    void authenticatesLinksListsAndChecksReadinessPerRole(String role)throws Exception{
        var u=user(role);var started=begin(u,UUID.randomUUID(),"WEB");String id=id(started);
        assertEquals(role.equals("brands")?"PAYMENT":"PAYOUT",started.body().path("purpose").asText());
        var completed=callback(started,role.equals("creators"),"CODE-"+UUID.randomUUID());assertEquals(200,completed.status(),completed.body().toString());
        assertEquals("LINKED",completed.body().path("status").asText());
        String method=firstMethod(u);assertEquals(200,call("POST","/user/"+u.account()+"/billing/validation",u.token(),Map.of("methodId",method)).status());
        var list=call("GET","/user/"+u.account()+"/billing",u.token(),null).body().toString();
        assertTrue(list.contains("***@example.com"));for(String secret:List.of("encrypted","referenceHash","payerId","access_token","SETUP-","VAULT-","business@","creator@"))assertFalse(list.contains(secret),secret);
        var rows=jdbc.queryForMap("select * from billing_authorization where authorization_id=?",id);
        assertNull(rows.get("encrypted_state"));assertNull(rows.get("encrypted_access"));assertNull(rows.get("encrypted_approval_url"));
    }
    @Test void repeatBeginUsesStableRequestAndRejectsDifferentClient()throws Exception{
        var u=user("brands");UUID key=UUID.randomUUID();var a=begin(u,key,"WEB");var b=begin(u,key,"WEB");assertEquals(id(a),id(b));
        assertEquals(409,begin(u,key,"ANDROID").status());assertEquals(a.body().path("approvalUrl"),b.body().path("approvalUrl"));
    }
    @Test void recoverPreparationAfterTransientFailureWithoutNewAuthorization()throws Exception{
        var u=user("brands");provider.failPrepare.add(UUID.fromString(u.account()));UUID key=UUID.randomUUID();var first=begin(u,key,"WEB");
        assertEquals("PREPARING",first.body().path("status").asText());assertEquals("PROVIDER_UNAVAILABLE",first.body().path("errorCode").asText());
        var retry=begin(u,key,"WEB");assertEquals(id(first),id(retry));assertEquals("PENDING_APPROVAL",retry.body().path("status").asText());
    }
    @Test void fundingCallbackIsIdempotentAndNeverTrustsClientApprovalFlag()throws Exception{
        var u=user("brands");var a=begin(u,UUID.randomUUID(),"WEB");UUID id=UUID.fromString(id(a));provider.pending.add(id);
        var early=callback(a,false,null);assertEquals("PENDING_APPROVAL",early.body().path("status").asText());assertEquals("AWAITING_APPROVAL",early.body().path("errorCode").asText());
        assertEquals(0,call("GET","/user/"+u.account()+"/billing",u.token(),null).body().path("total").asInt());
        provider.pending.remove(id);assertEquals("LINKED",callback(a,false,null).body().path("status").asText());
        int calls=provider.completions.get();assertEquals("LINKED",callback(a,false,null).body().path("status").asText());assertEquals(calls,provider.completions.get());
        assertEquals(1,call("GET","/user/"+u.account()+"/billing",u.token(),null).body().path("total").asInt());
    }
    @Test void identityCallbackCachesTokenAndDoesNotExchangeCodeAgain()throws Exception{
        var u=user("creators");var a=begin(u,UUID.randomUUID(),"WEB");String code="C-"+UUID.randomUUID();provider.failIdentity.add(code);
        assertEquals("PENDING_APPROVAL",callback(a,true,code).body().path("status").asText());
        assertEquals("LINKED",callback(a,true,code).body().path("status").asText());assertEquals("LINKED",callback(a,true,code).body().path("status").asText());
        assertEquals(1,provider.exchanges.get(code).get());assertEquals(409,callback(a,true,"DIFFERENT").status());
    }
    @Test void ambiguousIdentityExchangeRequiresNewConsentWithoutReusingCode()throws Exception{
        var u=user("creators");var a=begin(u,UUID.randomUUID(),"WEB");String code="AMBIGUOUS-"+UUID.randomUUID();provider.uncertainExchange.add(code);
        var failed=callback(a,true,code);assertEquals("FAILED",failed.body().path("status").asText());
        assertEquals("PROVIDER_UNAVAILABLE",failed.body().path("errorCode").asText());
        assertEquals("FAILED",callback(a,true,code).body().path("status").asText());
        assertEquals(1,provider.exchanges.get(code).get());
        assertEquals(0,call("GET","/user/"+u.account()+"/billing",u.token(),null).body().path("total").asInt());
    }
    @Test void financialAccountCannotBeClaimedByAnotherUser()throws Exception{
        var first=user("brands");var second=user("brands");var a=begin(first,UUID.randomUUID(),"WEB");var b=begin(second,UUID.randomUUID(),"WEB");
        String payer=FakeGateway.payer(UUID.randomUUID());provider.payers.put(UUID.fromString(id(a)),payer);provider.payers.put(UUID.fromString(id(b)),payer);
        assertEquals("LINKED",callback(a,false,null).body().path("status").asText());var denied=callback(b,false,null);
        assertEquals("FAILED",denied.body().path("status").asText());assertEquals("OWNERSHIP_CONFLICT",denied.body().path("errorCode").asText());
    }
    @Test void concurrentOwnersCannotClaimSameVerifiedPayer()throws Exception{
        var first=user("brands");var second=user("brands");var a=begin(first,UUID.randomUUID(),"WEB");var b=begin(second,UUID.randomUUID(),"WEB");
        String payer=FakeGateway.payer(UUID.randomUUID());provider.payers.put(UUID.fromString(id(a)),payer);provider.payers.put(UUID.fromString(id(b)),payer);
        var pool=Executors.newFixedThreadPool(2);
        try{
            var responses=pool.invokeAll(List.<Callable<Result>>of(()->callback(a,false,null),()->callback(b,false,null)));
            var states=new HashSet<String>();for(var response:responses){var r=response.get();assertEquals(200,r.status(),r.body().toString());states.add(r.body().path("status").asText());}
            assertEquals(Set.of("LINKED","FAILED"),states);
        }finally{pool.shutdownNow();}
    }
    @Test void consentRenewalUpdatesSameOwnedMethod()throws Exception{
        var u=user("brands");var a=begin(u,UUID.randomUUID(),"WEB");var b=begin(u,UUID.randomUUID(),"WEB");String payer=FakeGateway.payer(UUID.randomUUID());
        provider.payers.put(UUID.fromString(id(a)),payer);provider.payers.put(UUID.fromString(id(b)),payer);
        callback(a,false,null);String method=firstMethod(u);callback(b,false,null);assertEquals(method,firstMethod(u));
        assertEquals(1,call("GET","/user/"+u.account()+"/billing",u.token(),null).body().path("total").asInt());
    }
    @Test void publicStateExpiryCancellationAndWrongPurposeAreHandled()throws Exception{
        var u=user("brands");var a=begin(u,UUID.randomUUID(),"WEB");
        assertEquals(400,callback(a,true,"CODE").status());
        jdbc.update("update billing_authorization set created_at=?,expires_at=? where authorization_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(3600)),java.sql.Timestamp.from(Instant.now().minusSeconds(1)),id(a));
        assertEquals("EXPIRED",callback(a,false,null).body().path("status").asText());
        var b=begin(u,UUID.randomUUID(),"WEB");
        assertEquals("CANCELLED",call("GET","/billing/paypal/callback?state="+state(b)+"&cancelled=true",null,null).body().path("status").asText());
        assertEquals("CANCELLED",callback(b,false,null).body().path("status").asText());
        assertEquals(400,call("GET","/billing/paypal/callback?state="+"a".repeat(43),null,null).status());
    }
    @Test void androidReturnContainsOnlySafeIdentifierAndStatus()throws Exception{
        var u=user("brands");var a=begin(u,UUID.randomUUID(),"ANDROID");var r=callback(a,false,null);assertEquals(303,r.status());
        assertEquals("collabpro://billing-return?authorizationId="+id(a)+"&status=LINKED",r.location());
        assertFalse(r.location().contains(state(a)));
    }
    @Test void ownershipAuthenticationInputAndMissingMethodAreEnforced()throws Exception{
        var u=user("brands");var other=user("creators");var a=begin(u,UUID.randomUUID(),"WEB");
        assertEquals(401,call("GET","/user/"+u.account()+"/billing",null,null).status());
        assertEquals(403,call("GET","/user/"+u.account()+"/billing",other.token(),null).status());
        assertEquals(403,call("GET","/billing/authorizations/"+id(a),other.token(),null).status());
        assertEquals(400,call("GET","/user/"+u.account()+"/billing?size=101",u.token(),null).status());
        assertEquals(400,call("POST","/user/"+u.account()+"/billing",u.token(),Map.of("client","WEB")).status());
        assertEquals(409,call("POST","/user/"+u.account()+"/billing/validation",u.token(),Map.of("methodId",UUID.randomUUID())).status());
    }
    @ParameterizedTest @ValueSource(strings={"VAULT.PAYMENT-TOKEN.DELETED","VAULT.PAYMENT-TOKEN.DELETION-INITIATED"})
    void verifiedWebhookRevokesReadinessAndDuplicatesHaveNoEffects(String type)throws Exception{
        var u=user("brands");var a=begin(u,UUID.randomUUID(),"WEB");callback(a,false,null);String method=firstMethod(u);
        var e=event("WH-"+UUID.randomUUID(),type,"VAULT-"+id(a));
        var processed=webhook(e,"valid");assertEquals(200,processed.status(),processed.body().toString());assertFalse(processed.body().path("duplicate").asBoolean());
        assertTrue(webhook(e,"valid").body().path("duplicate").asBoolean());
        assertEquals("REVOKED",call("GET","/user/"+u.account()+"/billing",u.token(),null).body().path("items").get(0).path("status").asText());
        assertEquals(409,call("POST","/user/"+u.account()+"/billing/validation",u.token(),Map.of("methodId",method)).status());
    }
    @Test void revocationBeforeCallbackWinsOverLaterCreationEvent()throws Exception{
        var u=user("brands");var a=begin(u,UUID.randomUUID(),"WEB");String reference="VAULT-"+id(a);
        assertEquals(200,webhook(event("WH-"+UUID.randomUUID(),"VAULT.PAYMENT-TOKEN.DELETED",reference),"valid").status());
        webhook(event("WH-"+UUID.randomUUID(),"VAULT.PAYMENT-TOKEN.CREATED",reference),"valid");
        var result=callback(a,false,null);assertEquals("FAILED",result.body().path("status").asText());assertEquals("PROVIDER_REJECTED",result.body().path("errorCode").asText());
        assertEquals(0,call("GET","/user/"+u.account()+"/billing",u.token(),null).body().path("total").asInt());
    }
    @Test void webhookRejectsUnverifiedAndAlteredDuplicatesAndDefersFuturePayments()throws Exception{
        String id="WH-"+UUID.randomUUID();var e=event(id,"PAYMENT.CAPTURE.COMPLETED","CAPTURE1");
        assertEquals(401,webhook(e,"invalid").status());assertEquals(0,jdbc.queryForObject("select count(*) from billing_webhook_event where event_id=?",Integer.class,id));
        assertEquals("DEFERRED",webhook(e,"valid").body().path("status").asText());
        assertEquals(409,webhook(event(id,"PAYMENT.CAPTURE.COMPLETED","OTHER"),"valid").status());
        assertEquals(401,webhook(e,"invalid").status());
        var encrypted=jdbc.queryForObject("select encrypted_payload from billing_webhook_event where event_id=?",String.class,id);assertFalse(encrypted.contains("CAPTURE1"));
    }
    @Test void webhookCanonicalReplayIgnoresJsonKeyOrdering()throws Exception{
        String id="WH-"+UUID.randomUUID();var e=new LinkedHashMap<String,Object>();e.put("id",id);e.put("event_type","UNSUPPORTED.EVENT");e.put("resource",Map.of("id","RESOURCE"));
        assertEquals(200,webhook(e,"valid").status());var reordered=new LinkedHashMap<String,Object>();reordered.put("resource",e.get("resource"));reordered.put("event_type",e.get("event_type"));reordered.put("id",id);
        assertTrue(webhook(reordered,"valid").body().path("duplicate").asBoolean());
    }
    @Test void cancellationIsIdempotentAndCannotUndoLinkedAuthorization()throws Exception{
        var u=user("brands");var a=begin(u,UUID.randomUUID(),"WEB");
        var one=call("POST","/billing/authorizations/"+id(a)+"/cancellation",u.token(),null);assertEquals("CANCELLED",one.body().path("status").asText());
        assertEquals("CANCELLED",call("POST","/billing/authorizations/"+id(a)+"/cancellation",u.token(),null).body().path("status").asText());
        var b=begin(u,UUID.randomUUID(),"WEB");callback(b,false,null);
        assertEquals("LINKED",call("POST","/billing/authorizations/"+id(b)+"/cancellation",u.token(),null).body().path("status").asText());
    }
    @RepeatedTest(3) void concurrentSignedWebhookDeliveriesAreAcknowledgedOnce()throws Exception{
        var u=user("brands");var a=begin(u,UUID.randomUUID(),"WEB");callback(a,false,null);
        var event=event("WH-"+UUID.randomUUID(),"VAULT.PAYMENT-TOKEN.DELETED","VAULT-"+id(a));var pool=Executors.newFixedThreadPool(4);
        try{var tasks=new ArrayList<Callable<Result>>();for(int n=0;n<4;n++)tasks.add(()->webhook(event,"valid"));
            int originals=0;for(var result:pool.invokeAll(tasks)){var r=result.get();assertEquals(200,r.status(),r.body().toString());if(!r.body().path("duplicate").asBoolean())originals++;}
            assertEquals(1,originals);
        }finally{pool.shutdownNow();}
    }
    @Test void webhookRejectsDuplicateKeysAndOversizedPayload()throws Exception{
        for(String raw:List.of("{\"id\":\"WH-ONE\",\"id\":\"WH-TWO\"}", "{\"data\":\""+ "x".repeat(262145)+"\"}")){
            var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/webhooks/payments")).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(raw)).build();
            assertEquals(400,http.send(req,HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
    @Test void concurrentFundingCallbacksLinkOneMethodAndOneAudit()throws Exception{
        var u=user("brands");var a=begin(u,UUID.randomUUID(),"WEB");var pool=Executors.newFixedThreadPool(4);
        try{var tasks=new ArrayList<Callable<Result>>();for(int n=0;n<4;n++)tasks.add(()->callback(a,false,null));
            for(var result:pool.invokeAll(tasks))assertEquals(200,result.get().status());}
        finally{pool.shutdownNow();}
        assertEquals(1,call("GET","/user/"+u.account()+"/billing",u.token(),null).body().path("total").asInt());
        assertEquals(1,jdbc.queryForObject("select count(*) from billing_audit where authorization_id=? and event_type='METHOD_LINKED'",Integer.class,id(a)));
    }
}
