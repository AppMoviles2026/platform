package com.collabtech.platform.billing;
import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.billing.application.ports.SubscriptionPorts.*;
import com.collabtech.platform.billing.application.services.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.time.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("test") @Import(SubscriptionApiTests.Fakes.class)
class SubscriptionApiTests {
    @TestConfiguration static class Fakes {
        @Bean @Primary BillingApiTests.FakeGateway subscriptionFundingGateway(){return new BillingApiTests.FakeGateway();}
        @Bean @Primary FakeProvider subscriptionProvider(){return new FakeProvider();}
    }
    static class FakeProvider implements Provider {
        final Map<String,Remote> states=new ConcurrentHashMap<>();final Map<UUID,String> approvalStates=new ConcurrentHashMap<>();
        final Set<UUID> failOnce=ConcurrentHashMap.newKeySet(),loseOnce=ConcurrentHashMap.newKeySet();final Set<String> rejectPendingCancel=ConcurrentHashMap.newKeySet();final Set<String> unavailable=ConcurrentHashMap.newKeySet();
        final java.util.concurrent.atomic.AtomicBoolean failNext=new java.util.concurrent.atomic.AtomicBoolean(),loseNext=new java.util.concurrent.atomic.AtomicBoolean();
        final AtomicInteger creations=new AtomicInteger(),cancellations=new AtomicInteger();
        public boolean configured(){return true;}public String returnUrl(){return "https://example.test/api/v1/billing/paypal/subscription-callback";}
        public ProviderPlan plan(String id){return new ProviderPlan(id,"ACTIVE",new BigDecimal(id.contains("AAAA")?"5.00":"10.00"),"USD",true);}
        public Created create(UUID id,String plan,String state){
            creations.incrementAndGet();if(failNext.getAndSet(false)||failOnce.remove(id))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);
            String reference="I-"+id.toString().replace("-","").toUpperCase(Locale.ROOT);approvalStates.put(id,state);
            states.putIfAbsent(reference,new Remote(reference,id.toString(),plan,"APPROVAL_PENDING",null,Instant.now(),null,0,null,List.of()));
            if(loseNext.getAndSet(false)||loseOnce.remove(id))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);
            return new Created(reference,"https://www.sandbox.paypal.com/approve?state="+state);
        }
        public Remote read(String id){if(unavailable.contains(id))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);return Objects.requireNonNull(states.get(id));}
        public void cancel(String id){cancellations.incrementAndGet();var r=states.get(id);if(rejectPendingCancel.contains(id)&&r.status().equals("APPROVAL_PENDING"))throw new BillingFailure(Code.PROVIDER_REJECTED);
            states.put(id,new Remote(id,r.customId(),r.planId(),"CANCELLED",r.payerId(),Instant.now(),null,0,null,r.charges()));}
        String reference(String local){return "I-"+local.replace("-","").toUpperCase(Locale.ROOT);}
    }
    @Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired FakeProvider provider;@Autowired BillingApiTests.FakeGateway funding;
    @Autowired Store store;@Autowired com.collabtech.platform.billing.application.ports.BillingPorts.UnitOfWork work;
    @Autowired SubscriptionEvents events;@Autowired SubscriptionService service;@LocalServerPort int port;
    final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    record Result(int status,JsonNode body,String location){}record User(String token,String account){}
    record Fixture(User user,String method,String payer){}
    Result call(String method,String path,String token,Object body)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(30));
        if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());return new Result(response.statusCode(),response.body().isBlank()?json.createObjectNode():json.readTree(response.body()),response.headers().firstValue("Location").orElse(null));
    }
    User user(String type)throws Exception{
        String email="subscription-"+UUID.randomUUID()+"@example.com",password="SubscriptionTest123!";
        assertEquals(201,call("POST","/auth/"+type,null,Map.of(type.equals("brands")?"businessName":"displayName","Demo","email",email,"password",password)).status());
        var login=call("POST","/auth/sessions",null,Map.of("email",email,"password",password));assertEquals(200,login.status());
        return new User(login.body().path("accessToken").asText(),login.body().path("account").path("accountId").asText());
    }
    Fixture fixture()throws Exception{
        var user=user("brands");var a=call("POST","/user/"+user.account()+"/billing",user.token(),Map.of("requestId",UUID.randomUUID(),"client","WEB"));
        String auth=a.body().path("authorizationId").asText();String state=funding.states.get(UUID.fromString(auth));
        assertEquals("LINKED",call("GET","/billing/paypal/callback?state="+state,null,null).body().path("status").asText());
        var method=call("GET","/user/"+user.account()+"/billing",user.token(),null).body().path("items").get(0).path("methodId").asText();
        return new Fixture(user,method,BillingApiTests.FakeGateway.payer(UUID.fromString(auth)));
    }
    String plan(User user)throws Exception{var result=call("GET","/subscription-plans",user.token(),null);assertEquals(200,result.status(),result.body().toString());return result.body().get(0).path("planId").asText();}
    Result begin(Fixture f,UUID request)throws Exception{return begin(f,request,"WEB");}
    Result begin(Fixture f,UUID request,String client)throws Exception{return call("POST","/user/"+f.user().account()+"/subscription",f.user().token(),Map.of("requestId",request,"planId",plan(f.user()),"methodId",f.method(),"client",client));}
    String id(Result r){assertEquals(201,r.status(),r.body().toString());return r.body().path("subscriptionId").asText();}
    Result refresh(Fixture f,String id)throws Exception{return call("POST","/subscriptions/"+id+"/refresh",f.user().token(),null);}
    Remote state(Fixture f,String id,String status,String paymentStatus,BigDecimal amount,Instant next){
        String ref=provider.reference(id);var initial=provider.states.get(ref);Instant paid=Instant.now().minusSeconds(1);
        var charges=paymentStatus==null?List.<Charge>of():List.of(new Charge("SALE-"+id.replace("-",""),paymentStatus,amount,"USD",paid));
        var value=new Remote(ref,id,initial.planId(),status,f.payer(),Instant.now(),next,paymentStatus!=null&&paymentStatus.equals("FAILED")?1:0,null,charges);
        provider.states.put(ref,value);return value;
    }
    Result webhook(String type,Map<String,Object> resource,String eventId,Instant time)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/webhooks/payments")).header("Content-Type","application/json").header("PAYPAL-TRANSMISSION-SIG","valid")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("id",eventId,"event_type",type,"create_time",time.toString(),"resource",resource)))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());return new Result(response.statusCode(),json.readTree(response.body()),null);
    }
    @Test void serverCatalogIncludesConfirmedDemoPrices()throws Exception{
        var user=user("brands");var plans=call("GET","/subscription-plans",user.token(),null).body();assertEquals(2,plans.size());
        assertEquals(0,new BigDecimal("5.00").compareTo(plans.get(0).path("amount").decimalValue()));assertEquals(0,new BigDecimal("10.00").compareTo(plans.get(1).path("amount").decimalValue()));
        for(var p:plans){assertEquals("USD",p.path("currency").asText());assertTrue(p.path("demonstration").asBoolean());assertTrue(p.path("available").asBoolean());assertFalse(p.has("providerPlanId"));}
    }
    @Test void beginsPendingAndActivatesOnlyAfterConfirmedCharge()throws Exception{
        var f=fixture();var a=begin(f,UUID.randomUUID());String id=id(a);assertEquals("APPROVAL_PENDING",a.body().path("status").asText());assertFalse(a.body().path("benefitsActive").asBoolean());
        state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().plusSeconds(29*86400));
        var active=refresh(f,id);assertEquals(200,active.status(),active.body().toString());assertEquals("ACTIVE",active.body().path("status").asText());assertTrue(active.body().path("benefitsActive").asBoolean());
        assertFalse(active.body().path("benefits").isEmpty());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM billing_subscription_payment WHERE subscription_id=?",Integer.class,id));
        assertTrue(call("GET","/user/"+f.user().account()+"/subscription",f.user().token(),null).body().path("benefitsActive").asBoolean());
    }
    @ParameterizedTest @ValueSource(strings={"FAILED","PENDING","DENIED","REFUNDED","NONE"})
    void failedOrUnconfirmedChargeDoesNotActivateBenefits(String payment)throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));state(f,id,"ACTIVE",payment.equals("NONE")?null:payment,new BigDecimal("5.00"),Instant.now().plusSeconds(29*86400));
        var result=refresh(f,id);assertFalse(result.body().path("benefitsActive").asBoolean());assertNotEquals("ACTIVE",result.body().path("status").asText());
    }
    @Test void rejectsWrongAmountAndExpiredPaidPeriod()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));state(f,id,"ACTIVE","COMPLETED",new BigDecimal("1.00"),Instant.now().plusSeconds(1000));
        assertFalse(refresh(f,id).body().path("benefitsActive").asBoolean());state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().minusSeconds(1));
        assertFalse(refresh(f,id).body().path("benefitsActive").asBoolean());
    }
    @Test void lostProviderResponseRecoversSameResourceAndRequestId()throws Exception{
        var f=fixture();provider.loseNext.set(true);UUID key=UUID.randomUUID();var first=begin(f,key);String id=id(first);
        assertEquals("PREPARING",first.body().path("status").asText());assertEquals("PROVIDER_UNAVAILABLE",first.body().path("errorCode").asText());
        var retry=begin(f,key);assertEquals(id,id(retry));assertEquals("APPROVAL_PENDING",retry.body().path("status").asText());
        assertEquals(1,provider.states.values().stream().filter(s->s.customId().equals(id)).count());
    }
    @Test void expiredIdempotencyWindowDoesNotCreateAnotherRemoteResource()throws Exception{
        var f=fixture();provider.loseNext.set(true);UUID key=UUID.randomUUID();String id=id(begin(f,key));
        work.execute(()->{var s=store.byId(UUID.fromString(id)).orElseThrow();s.attemptedAt=Instant.now().minusSeconds(49*3600);store.save(s);return null;});
        int before=provider.creations.get();var retry=begin(f,key);assertEquals("RECONCILIATION_REQUIRED",retry.body().path("status").asText());
        assertEquals("IDEMPOTENCY_WINDOW_EXPIRED",retry.body().path("errorCode").asText());assertEquals(before,provider.creations.get());assertEquals(409,begin(f,UUID.randomUUID()).status());
    }
    @Test void newerSignedFailureBlocksBenefitsUntilANewerConfirmedCharge()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));var old=state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().plusSeconds(86400));refresh(f,id);
        Instant failedAt=Instant.now();webhook("BILLING.SUBSCRIPTION.PAYMENT.FAILED",Map.of("id",old.id()),"WH-"+UUID.randomUUID(),failedAt);events.process(100);
        var failed=refresh(f,id).body();assertEquals("PAYMENT_FAILED",failed.path("status").asText());assertFalse(failed.path("benefitsActive").asBoolean());
        var paid=new Charge("RECOVERED-"+id.replace("-",""),"COMPLETED",new BigDecimal("5.00"),"USD",failedAt.plusSeconds(1));
        provider.states.put(old.id(),new Remote(old.id(),id,old.planId(),"ACTIVE",f.payer(),Instant.now(),Instant.now().plusSeconds(86400),0,null,List.of(paid)));
        assertTrue(refresh(f,id).body().path("benefitsActive").asBoolean());
    }
    @Test void queuedEventRemainsRetryableWhenProviderIsDown()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));String event="WH-"+UUID.randomUUID();provider.unavailable.add(provider.reference(id));
        webhook("BILLING.SUBSCRIPTION.ACTIVATED",Map.of("id",provider.reference(id),"custom_id",id),event,Instant.now());events.process(100);
        assertEquals("QUEUED",jdbc.queryForObject("SELECT status FROM billing_webhook_event WHERE event_id=?",String.class,event));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM billing_subscription_event WHERE event_id=?",String.class,event));
        provider.unavailable.remove(provider.reference(id));state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().plusSeconds(86400));
        jdbc.update("UPDATE billing_subscription_event SET next_attempt_at=? WHERE event_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),event);
        events.process(100);assertEquals("PROCESSED",jdbc.queryForObject("SELECT status FROM billing_webhook_event WHERE event_id=?",String.class,event));
    }
    @Test void idempotentRequestAndOnlyOneCurrentAttempt()throws Exception{
        var f=fixture();var key=UUID.randomUUID();var first=begin(f,key);String id=id(first);assertEquals(id,id(begin(f,key)));
        assertEquals(409,begin(f,key,"ANDROID").status());assertEquals(409,begin(f,UUID.randomUUID()).status());
        var count=jdbc.queryForObject("SELECT COUNT(*) FROM billing_subscription WHERE payment_account_id=(SELECT payment_account_id FROM billing_account WHERE account_id=?)",Integer.class,f.user().account());assertEquals(1,count);
    }
    @Test void noMethodNoCreatorAndNoForeignAccountCanSubscribe()throws Exception{
        var brand=user("brands");String p=plan(brand);
        assertEquals("METHOD_REQUIRED",call("POST","/user/"+brand.account()+"/subscription",brand.token(),Map.of("requestId",UUID.randomUUID(),"planId",p,"methodId",UUID.randomUUID(),"client","WEB")).body().path("code").asText());
        var creator=user("creators");assertEquals(403,call("GET","/subscription-plans",creator.token(),null).status());
        assertEquals(403,call("GET","/user/"+brand.account()+"/subscription",creator.token(),null).status());assertEquals(401,call("GET","/subscription-plans",null,null).status());
        assertEquals(400,call("POST","/user/"+brand.account()+"/subscription",brand.token(),Map.of("requestId",UUID.randomUUID())).status());
    }
    @Test void differentProviderPayerCannotActivateSubscription()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));var r=state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().plusSeconds(86400));
        provider.states.put(r.id(),new Remote(r.id(),r.customId(),r.planId(),r.status(),"ZZZZZZZZZZZZZ",r.updatedAt(),r.nextBillingAt(),0,null,r.charges()));
        var result=refresh(f,id).body();assertFalse(result.path("benefitsActive").asBoolean());assertEquals("PROVIDER_ACCOUNT_MISMATCH",result.path("errorCode").asText());
        assertEquals("CANCELLED",call("POST","/subscriptions/"+id+"/cancellation",f.user().token(),null).body().path("status").asText());
    }
    @Test void pendingCancellationCanResumeOriginalApprovalWithoutCreatingAnotherSubscription()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));provider.rejectPendingCancel.add(provider.reference(id));
        var cancel=call("POST","/subscriptions/"+id+"/cancellation",f.user().token(),null);assertEquals("CANCEL_PENDING",cancel.body().path("status").asText());
        var resumed=call("POST","/subscriptions/"+id+"/resume-approval",f.user().token(),Map.of("expectedVersion",cancel.body().path("version").asLong()));
        assertEquals(200,resumed.status(),resumed.body().toString());assertEquals("APPROVAL_PENDING",resumed.body().path("status").asText());assertTrue(resumed.body().path("approvalUrl").asText().startsWith("https://"));
    }
    @Test void staleBrowserCancelReturnCannotCancelPaidSubscription()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));String state=provider.approvalStates.get(UUID.fromString(id));
        state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().plusSeconds(86400));refresh(f,id);
        int before=provider.cancellations.get();var result=call("GET","/billing/paypal/subscription-callback?state="+state+"&cancelled=true",null,null);
        assertEquals("ACTIVE",result.body().path("status").asText());assertEquals(before,provider.cancellations.get());
    }
    @Test void providerCancellationIsConfirmedAndAllowsLaterSubscription()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().plusSeconds(86400));refresh(f,id);
        var cancel=call("POST","/subscriptions/"+id+"/cancellation",f.user().token(),null);assertEquals("CANCELLED",cancel.body().path("status").asText());assertFalse(cancel.body().path("benefitsActive").asBoolean());
        int count=provider.cancellations.get();call("POST","/subscriptions/"+id+"/cancellation",f.user().token(),null);assertEquals(count,provider.cancellations.get());
        assertEquals(201,begin(f,UUID.randomUUID()).status());assertEquals(2,call("GET","/user/"+f.user().account()+"/subscriptions",f.user().token(),null).body().path("total").asInt());
    }
    @Test void unavailableProviderPreservesSnapshotWithoutFalseSuccess()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));provider.unavailable.add(provider.reference(id));
        var result=refresh(f,id).body();assertEquals("APPROVAL_PENDING",result.path("status").asText());assertFalse(result.path("benefitsActive").asBoolean());assertEquals("PROVIDER_UNAVAILABLE",result.path("errorCode").asText());
        assertEquals(409,begin(f,UUID.randomUUID()).status());
    }
    @Test void androidCallbackIsSafeAndIdempotent()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID(),"ANDROID"));var state=provider.approvalStates.get(UUID.fromString(id));
        var result=call("GET","/billing/paypal/subscription-callback?state="+state,null,null);assertEquals(303,result.status());
        assertEquals("collabpro://subscription-return?subscriptionId="+id+"&status=APPROVAL_PENDING",result.location());
        assertFalse(result.location().contains(state));assertEquals(303,call("GET","/billing/paypal/subscription-callback?state="+state,null,null).status());
        assertEquals(400,call("GET","/billing/paypal/subscription-callback?state="+"x".repeat(43),null,null).status());
    }
    @Test void signedEventsAreQueuedDeduplicatedAndReconciledFromProvider()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().plusSeconds(86400));
        Instant timestamp=Instant.now();String event="WH-"+UUID.randomUUID();var resource=Map.<String,Object>of("id",provider.reference(id),"custom_id",id);
        assertEquals("QUEUED",webhook("BILLING.SUBSCRIPTION.ACTIVATED",resource,event,timestamp).body().path("status").asText());
        assertTrue(webhook("BILLING.SUBSCRIPTION.ACTIVATED",resource,event,timestamp).body().path("duplicate").asBoolean());
        assertEquals(409,webhook("BILLING.SUBSCRIPTION.ACTIVATED",resource,event,timestamp.plusSeconds(1)).status());
        events.process(100);assertEquals("PROCESSED",jdbc.queryForObject("SELECT status FROM billing_webhook_event WHERE event_id=?",String.class,event));
        assertTrue(call("GET","/user/"+f.user().account()+"/subscription",f.user().token(),null).body().path("benefitsActive").asBoolean());
    }
    @Test void refundCannotBeUndoneByStaleCompletedTransaction()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));var r=state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().plusSeconds(86400));refresh(f,id);
        var e=webhook("PAYMENT.SALE.REFUNDED",Map.of("sale_id",r.charges().get(0).id(),"billing_agreement_id",r.id()),"WH-"+UUID.randomUUID(),Instant.now());assertEquals(200,e.status());
        events.process(100);assertFalse(refresh(f,id).body().path("benefitsActive").asBoolean());
    }
    @Test void oldProviderSnapshotCannotReactivateCancelledSubscription()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));var old=state(f,id,"ACTIVE","COMPLETED",new BigDecimal("5.00"),Instant.now().plusSeconds(86400));refresh(f,id);
        call("POST","/subscriptions/"+id+"/cancellation",f.user().token(),null);provider.states.put(old.id(),old);
        assertEquals("CANCELLED",refresh(f,id).body().path("status").asText());
    }
    @RepeatedTest(2) void concurrentRequestsCreateOnlyOneSubscription()throws Exception{
        var f=fixture();var key=UUID.randomUUID();var pool=Executors.newFixedThreadPool(3);String plan=plan(f.user());
        try{var tasks=new ArrayList<Callable<Result>>();for(int n=0;n<3;n++)tasks.add(()->call("POST","/user/"+f.user().account()+"/subscription",f.user().token(),Map.of("requestId",key,"planId",plan,"methodId",f.method(),"client","WEB")));
            var ids=new HashSet<String>();for(var future:pool.invokeAll(tasks)){var r=future.get();ids.add(id(r));}assertEquals(1,ids.size());
        }finally{pool.shutdownNow();}
    }
    @Test void queriesExposeNoProviderReferencesOrConsentSecrets()throws Exception{
        var f=fixture();String id=id(begin(f,UUID.randomUUID()));var other=user("brands");
        assertEquals(403,call("GET","/subscriptions/"+id,other.token(),null).status());
        var result=call("GET","/user/"+f.user().account()+"/subscriptions",f.user().token(),null).body().toString();
        for(String secret:List.of("encryptedProvider","stateHash","payerHash","requestHash","encryptedState"))assertFalse(result.contains(secret));
        assertEquals("NONE",call("GET","/user/"+other.account()+"/subscription",other.token(),null).body().path("status").asText());
    }
}
