package com.collabtech.platform.collaboration;
import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.billing.application.ports.BillingPorts;
import com.collabtech.platform.billing.application.ports.CompensationPorts.*;
import com.collabtech.platform.billing.application.services.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import tools.jackson.databind.JsonNode;

@Import(CompensationApiTests.Fakes.class)
class CompensationApiTests extends IncidentApiFixture {
    @TestConfiguration static class Fakes {
        @Bean @Primary FakeWallet compensationWallet(){return new FakeWallet();}
        @Bean @Primary FakePayments compensationProvider(){return new FakePayments();}
    }
    static class FakeWallet implements BillingPorts.Gateway {
        final Map<UUID,UUID> owners=new ConcurrentHashMap<>();
        public BillingPorts.Configuration configuration(){return new BillingPorts.Configuration(true,true,true,"SANDBOX","https://example.test/callback","https://example.test/identity","collabpro://billing-return",1800);}
        static String payer(String account){return account.replace("-","").substring(0,13).toUpperCase(Locale.ROOT);}
        public BillingPorts.Preparation prepare(UUID auth,UUID account,String state){owners.put(auth,account);return new BillingPorts.Preparation("SETUP-"+auth,"CUSTOMER-"+account,"https://www.sandbox.paypal.com/approve?state="+state);}
        public String identityApproval(String state){return "https://www.sandbox.paypal.com/connect?state="+state;}
        public BillingPorts.Verified completeFunding(UUID auth,UUID account,String setup,String customer){return new BillingPorts.Verified("VAULT-"+auth,payer(account.toString()),"payer@example.invalid");}
        public BillingPorts.Token exchangeIdentity(String code){return new BillingPorts.Token("TOKEN-"+code,Instant.now().plusSeconds(3600));}
        public BillingPorts.Verified identity(String token){String id=payer(token.substring(6));return new BillingPorts.Verified(id,id,"creator@example.invalid");}
        public boolean verify(BillingPorts.Signature signature,Map<String,Object> body){return signature!=null&&"valid".equals(signature.signature());}
    }
    static class FakePayments implements Provider {
        final Map<String,Order> orders=new ConcurrentHashMap<>();final Map<String,Payout> payouts=new ConcurrentHashMap<>();
        final Map<UUID,String> orderByComp=new ConcurrentHashMap<>(),payoutByComp=new ConcurrentHashMap<>(),states=new ConcurrentHashMap<>(),payoutStatus=new ConcurrentHashMap<>();
        final Set<String> unavailable=ConcurrentHashMap.newKeySet();
        final AtomicInteger created=new AtomicInteger(),captured=new AtomicInteger(),sent=new AtomicInteger();
        final AtomicBoolean loseOrder=new AtomicBoolean(),loseCapture=new AtomicBoolean(),losePayout=new AtomicBoolean(),offline=new AtomicBoolean();
        public void preflight(){if(offline.get())throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);}
        public Settings settings(){return new Settings(true,Set.of("USD"),"https://example.test/api/v1/billing/paypal/compensation-callback",18000,2419200,true);}
        public Created createOrder(Funding f){
            created.incrementAndGet();String id="O"+f.operationId().toString().replace("-","").toUpperCase(Locale.ROOT);
            orders.putIfAbsent(id,new Order(id,"CREATED",f.compensationId().toString(),f.operationId().toString(),null,f.amount(),f.currency(),Instant.now(),List.of()));
            orderByComp.put(f.compensationId(),id);states.put(f.compensationId(),f.state());
            if(loseOrder.getAndSet(false))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);
            return new Created(id,"https://www.sandbox.paypal.com/approve?state="+f.state());
        }
        public Order readOrder(String id){if(unavailable.contains(id))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);return Objects.requireNonNull(orders.get(id));}
        public void approve(UUID comp,String account){String id=orderByComp.get(comp);var o=orders.get(id);orders.put(id,new Order(id,"APPROVED",o.compensationId(),o.operationId(),FakeWallet.payer(account),o.amount(),o.currency(),Instant.now(),o.captures()));}
        public void capture(String id,UUID operation){
            captured.incrementAndGet();var o=orders.get(id);
            var charge=new Capture("C"+operation.toString().replace("-","").toUpperCase(Locale.ROOT),"COMPLETED",o.amount(),o.currency(),Instant.now());
            orders.put(id,new Order(id,"COMPLETED",o.compensationId(),o.operationId(),o.payerId(),o.amount(),o.currency(),Instant.now(),List.of(charge)));
            if(loseCapture.getAndSet(false))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);
        }
        public Payout createPayout(UUID operation,UUID compensation,String receiver,BigDecimal amount,String currency){
            sent.incrementAndGet();String id="B"+operation.toString().replace("-","").toUpperCase(Locale.ROOT);
            String status=payoutStatus.getOrDefault(compensation,"SUCCESS");
            payouts.putIfAbsent(id,new Payout(id,"I"+operation.toString().replace("-","").toUpperCase(Locale.ROOT),operation.toString(),operation.toString(),receiver,status,amount,currency,"T"+operation.toString().replace("-","").toUpperCase(Locale.ROOT),Instant.now()));
            payoutByComp.put(compensation,id);
            if(losePayout.getAndSet(false))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);return payouts.get(id);
        }
        public Payout readPayout(String id){if(unavailable.contains(id))throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);return Objects.requireNonNull(payouts.get(id));}
        public Payout readPayoutItem(String id){return payouts.values().stream().filter(p->p.itemId().equals(id)).findFirst().orElseThrow();}
    }
    @Autowired FakePayments bank;@Autowired CompensationService compensation;@Autowired CompensationEvents compensationEvents;
    record Financial(Execution execution,String method){}
    Execution arrangement(String type,String currency)throws Exception{
        var brand=user("brands");var creator=user("creators");
        var campaign=call("POST","/campaigns",brand.token(),Map.of("title","Colaboración financiera","objective","Demo","category","Food","targetAudience","Lima"));
        assertEquals(201,campaign.status());String id=campaign.body().path("id").asText();
        var pay=new HashMap<String,Object>();pay.put("type",type);pay.put("description","Compensación acordada de prueba");
        if(type.equals("CASH")){pay.put("amount",5);pay.put("currency",currency);}
        var terms=Map.of("requirements",List.of(Map.of("description","Contenido original","mandatory",false)),
            "deliverables",List.of(Map.of("contentType","VIDEO","description","Video","quantity",1,"deadline",Instant.now().plusSeconds(7200).toString())),
            "applicationDeadline",Instant.now().plusSeconds(3600).toString(),"compensation",pay);
        assertEquals(200,call("PUT","/campaigns/"+id+"/conditions",brand.token(),terms).status());
        assertEquals(200,call("POST","/campaigns/"+id+"/publication",brand.token(),null).status());
        var app=call("POST","/campaigns/"+id+"/applications",creator.token(),Map.of("message","Propuesta"));assertEquals(201,app.status());String application=app.body().path("id").asText();
        assertEquals(200,call("POST","/campaigns/"+id+"/applications/"+application+"/decision",brand.token(),Map.of("decision","SELECTED","expectedVersion",0)).status());
        var fixture=new Fixture(brand,creator,id,application);String collaboration=active(fixture).path("collaborationId").asText();
        var view=call("GET","/collaborations/"+collaboration,creator.token(),null).body();
        return new Execution(fixture,collaboration,view.path("deliverables").get(0).path("deliverableId").asText(),Instant.parse(view.path("deliverables").get(0).path("deadline").asText()));
    }
    String link(User user,boolean creator)throws Exception{
        var a=call("POST","/user/"+user.account()+"/billing",user.token(),Map.of("requestId",UUID.randomUUID(),"client","WEB"));assertEquals(202,a.status(),a.body().toString());
        String state=a.body().path("approvalUrl").asText().split("state=")[1];
        var linked=call("GET","/billing/paypal/"+(creator?"identity-callback":"callback")+"?state="+state+(creator?"&code="+user.account():""),null,null);
        assertEquals("LINKED",linked.body().path("status").asText());
        return call("GET","/user/"+user.account()+"/billing",user.token(),null).body().path("items").get(0).path("methodId").asText();
    }
    JsonNode money(Execution e,User user)throws Exception{var r=call("GET","/collaborations/"+e.collaboration()+"/compensation",user.token(),null);assertEquals(200,r.status(),r.body().toString());return r.body();}
    UUID root(Execution e)throws Exception{return UUID.fromString(money(e,e.fixture().brand()).path("compensationId").asText());}
    Financial ready(String currency)throws Exception{
        var e=arrangement("CASH",currency);assertEquals(200,approve(e,piece(submit(e,"approved"))).status());
        String method=link(e.fixture().brand(),false);link(e.fixture().creator(),true);return new Financial(e,method);
    }
    Result start(Financial f,UUID request)throws Exception{return start(f,request,"WEB");}
    Result start(Financial f,UUID request,String client)throws Exception{
        var e=f.execution();return call("POST","/collaborations/"+e.collaboration()+"/payment",e.fixture().brand().token(),
            Map.of("requestId",request,"methodId",f.method(),"client",client,"expectedVersion",money(e,e.fixture().brand()).path("version").asLong()));
    }
    Result confirm(Financial f,UUID request)throws Exception{
        var e=f.execution();return call("POST","/collaborations/"+e.collaboration()+"/payment/confirmation",e.fixture().brand().token(),
            Map.of("requestId",request,"expectedVersion",money(e,e.fixture().brand()).path("version").asLong()));
    }
    Result pay(Financial f)throws Exception{
        var a=start(f,UUID.randomUUID());assertEquals(201,a.status(),a.body().toString());bank.approve(UUID.fromString(a.body().path("compensationId").asText()),f.execution().fixture().brand().account());
        return confirm(f,UUID.randomUUID());
    }
    Result signed(String type,Map<String,Object> resource,String id)throws Exception{
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/webhooks/payments")).header("Content-Type","application/json").header("PAYPAL-TRANSMISSION-SIG","valid")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("id",id,"event_type",type,"resource",resource)))).build();
        var res=http.send(req,HttpResponse.BodyHandlers.ofString());return new Result(res.statusCode(),json.readTree(res.body()));
    }
    @Test void compensationRemainsPendingUntilAllDeliverablesAreApproved()throws Exception{
        var e=arrangement("CASH","USD");var before=money(e,e.fixture().creator());assertEquals("PENDING_DELIVERABLES",before.path("status").asText());assertFalse(before.path("wasPaid").asBoolean());
        assertEquals(409,call("POST","/collaborations/"+e.collaboration()+"/payment",e.fixture().brand().token(),Map.of("requestId",UUID.randomUUID(),"client","WEB","expectedVersion",before.path("version").asLong())).status());
        assertEquals(200,approve(e,piece(submit(e,"ready"))).status());assertEquals("READY_FOR_PAYMENT",money(e,e.fixture().brand()).path("status").asText());
    }
    @Test void paysCreatorOnlyAfterCompanyCaptureAndKeepsFrozenTerms()throws Exception{
        var f=ready("USD");var e=f.execution();var original=collaboration(e,e.fixture().creator()).path("snapshot");
        int captures=bank.captured.get(),payouts=bank.sent.get();var paid=pay(f);assertEquals(200,paid.status(),paid.body().toString());assertEquals("PAID",paid.body().path("status").asText());
        assertTrue(paid.body().path("wasPaid").asBoolean());assertFalse(paid.body().path("fundedAt").isNull());assertFalse(paid.body().path("paidAt").isNull());
        assertEquals(captures+1,bank.captured.get());assertEquals(payouts+1,bank.sent.get());assertEquals(original,collaboration(e,e.fixture().brand()).path("snapshot"));
        assertEquals("PAID",money(e,e.fixture().creator()).path("status").asText());
    }
    @Test void orderApprovalAndBrowserReturnDoNotChargeCompany()throws Exception{
        var f=ready("USD");var result=start(f,UUID.randomUUID());assertEquals(201,result.status());UUID id=UUID.fromString(result.body().path("compensationId").asText());
        int before=bank.captured.get();bank.approve(id,f.execution().fixture().brand().account());
        var callback=call("GET","/billing/paypal/compensation-callback?state="+bank.states.get(id),null,null);
        assertEquals(200,callback.status());assertEquals(before,bank.captured.get());assertFalse(money(f.execution(),f.execution().fixture().creator()).path("wasPaid").asBoolean());
    }
    @Test void penIsPreservedAndCannotBeSilentlyConverted()throws Exception{
        var f=ready("PEN");var value=money(f.execution(),f.execution().fixture().creator());assertEquals("PEN",value.path("currency").asText());assertEquals("UNSUPPORTED_PAYMENT_CURRENCY",value.path("unavailableReason").asText());
        int before=bank.created.get();var result=start(f,UUID.randomUUID());assertEquals(409,result.status());assertEquals("UNSUPPORTED_PAYMENT_CURRENCY",result.body().path("code").asText());assertEquals(before,bank.created.get());
    }
    @ParameterizedTest @ValueSource(strings={"PRODUCT","SERVICE","CREDIT","BARTER"})
    void nonMonetaryCompensationNeverCreatesFinancialOperations(String type)throws Exception{
        var e=arrangement(type,null);assertEquals(200,approve(e,piece(submit(e,"barter"))).status());
        var value=money(e,e.fixture().creator());assertEquals("NON_MONETARY",value.path("status").asText());assertFalse(value.path("paymentApplicable").asBoolean());assertFalse(value.path("wasPaid").asBoolean());
        assertEquals(409,call("POST","/collaborations/"+e.collaboration()+"/payment",e.fixture().brand().token(),Map.of("requestId",UUID.randomUUID(),"client","WEB","expectedVersion",value.path("version").asLong())).status());
    }
    @Test void missingFinancialMethodsDoNotStartPayment()throws Exception{
        var e=arrangement("CASH","USD");assertEquals(200,approve(e,piece(submit(e,"missing-method"))).status());var value=money(e,e.fixture().brand());
        var result=call("POST","/collaborations/"+e.collaboration()+"/payment",e.fixture().brand().token(),Map.of("requestId",UUID.randomUUID(),"client","WEB","methodId",UUID.randomUUID(),"expectedVersion",value.path("version").asLong()));
        assertEquals(409,result.status());assertEquals("METHOD_REQUIRED",result.body().path("code").asText());
    }
    @Test void incidentBlocksCaptureAndResolutionAllowsSameIntent()throws Exception{
        var f=ready("USD");var e=f.execution();var started=start(f,UUID.randomUUID());assertEquals(201,started.status());bank.approve(UUID.fromString(started.body().path("compensationId").asText()),e.fixture().brand().account());
        var incident=openIncident(e,e.fixture().creator(),"Revisar cumplimiento");int before=bank.captured.get();UUID confirm=UUID.randomUUID();
        assertEquals("BLOCKED_INCIDENT",money(e,e.fixture().brand()).path("status").asText());assertEquals(409,confirm(f,confirm).status());assertEquals(before,bank.captured.get());
        resolveKeep(e,incident,e.fixture().brand(),e.fixture().creator());assertEquals("PAID",confirm(f,confirm).body().path("status").asText());
    }
    @Test void incidentAfterPaymentDoesNotEraseItOrRefundAutomatically()throws Exception{
        var f=ready("USD");var e=f.execution();var paid=pay(f);var time=paid.body().path("paidAt");openIncident(e,e.fixture().creator(),"Duda posterior");
        var result=money(e,e.fixture().brand());assertEquals("PAID",result.path("status").asText());assertEquals(time,result.path("paidAt"));assertEquals(1,result.path("openIncidentCount").asInt());assertTrue(result.path("wasPaid").asBoolean());
    }
    @Test void payoutFailureRetriesWithoutChargingCompanyAgain()throws Exception{
        var f=ready("USD");UUID id=root(f.execution());bank.payoutStatus.put(id,"FAILED");var failed=pay(f);assertEquals("PAYMENT_FAILED",failed.body().path("status").asText());assertFalse(failed.body().path("wasPaid").asBoolean());
        int captures=bank.captured.get();bank.payoutStatus.put(id,"SUCCESS");var retry=start(f,UUID.randomUUID());assertEquals(201,retry.status(),retry.body().toString());assertEquals("PAID",retry.body().path("status").asText());assertEquals(captures,bank.captured.get());
    }
    @Test void unclaimedPayoutIsNotPaidAndSignedConfirmationUsesActualProviderState()throws Exception{
        var f=ready("USD");UUID id=root(f.execution());bank.payoutStatus.put(id,"UNCLAIMED");var result=pay(f);assertEquals("PAYOUT_PENDING",result.body().path("status").asText());assertFalse(result.body().path("wasPaid").asBoolean());
        var old=bank.payouts.get(bank.payoutByComp.get(id));var complete=new Payout(old.batchId(),old.itemId(),old.senderBatchId(),old.senderItemId(),old.receiver(),"SUCCESS",old.amount(),old.currency(),old.transactionId(),Instant.now());bank.payouts.put(old.batchId(),complete);
        String event="WH-"+UUID.randomUUID();var resource=Map.<String,Object>of("payout_item_id",old.itemId(),"payout_batch_id",old.batchId(),"payout_item",Map.of("sender_item_id",old.senderItemId()));
        assertEquals("QUEUED",signed("PAYMENT.PAYOUTS-ITEM.SUCCEEDED",resource,event).body().path("status").asText());assertTrue(signed("PAYMENT.PAYOUTS-ITEM.SUCCEEDED",resource,event).body().path("duplicate").asBoolean());
        compensationEvents.process(100);assertEquals("PAID",money(f.execution(),f.execution().fixture().creator()).path("status").asText());
    }
    @Test void incidentAfterCaptureHoldsPayoutUntilBilateralResolution()throws Exception{
        var f=ready("USD");var e=f.execution();var started=start(f,UUID.randomUUID());UUID id=UUID.fromString(started.body().path("compensationId").asText());
        bank.approve(id,e.fixture().brand().account());bank.loseCapture.set(true);int before=bank.sent.get();
        assertEquals(200,confirm(f,UUID.randomUUID()).status());var incident=openIncident(e,e.fixture().creator(),"Revisar antes de enviar");
        compensation.reconcile(id,true);var blocked=money(e,e.fixture().brand());assertEquals("BLOCKED_INCIDENT",blocked.path("status").asText());
        assertFalse(blocked.path("fundedAt").isNull());assertEquals(before,bank.sent.get());int charges=bank.captured.get();
        resolveKeep(e,incident,e.fixture().brand(),e.fixture().creator());compensation.reconcile(id,true);
        assertEquals("PAID",money(e,e.fixture().brand()).path("status").asText());assertEquals(charges,bank.captured.get());
    }
    @Test void fundingReversalNeverErasesPaymentOrAutomaticallyChargesAgain()throws Exception{
        var f=ready("USD");var paid=pay(f);UUID id=UUID.fromString(paid.body().path("compensationId").asText());var o=bank.orders.get(bank.orderByComp.get(id));var capture=o.captures().getFirst();
        bank.orders.put(o.id(),new Order(o.id(),o.status(),o.compensationId(),o.operationId(),o.payerId(),o.amount(),o.currency(),Instant.now(),
            List.of(new Capture(capture.id(),"REFUNDED",capture.amount(),capture.currency(),Instant.now()))));
        int charges=bank.captured.get();var result=money(f.execution(),f.execution().fixture().brand());
        assertEquals("PAYMENT_REVERSED",result.path("status").asText());assertTrue(result.path("wasPaid").asBoolean());assertEquals(paid.body().path("paidAt"),result.path("paidAt"));
        assertEquals(409,start(f,UUID.randomUUID()).status());assertEquals(charges,bank.captured.get());
    }
    @Test void mismatchedProviderAmountCannotBeCaptured()throws Exception{
        var f=ready("USD");var started=start(f,UUID.randomUUID());UUID id=UUID.fromString(started.body().path("compensationId").asText());bank.approve(id,f.execution().fixture().brand().account());
        var o=bank.orders.get(bank.orderByComp.get(id));bank.orders.put(o.id(),new Order(o.id(),o.status(),o.compensationId(),o.operationId(),o.payerId(),new BigDecimal("9.00"),o.currency(),Instant.now(),List.of()));
        int before=bank.captured.get();assertEquals(409,confirm(f,UUID.randomUUID()).status());assertEquals(before,bank.captured.get());assertFalse(money(f.execution(),f.execution().fixture().creator()).path("wasPaid").asBoolean());
    }
    @Test void invalidPublicReturnCannotCreateOrCapturePayment()throws Exception{
        int before=bank.captured.get();assertEquals(400,call("GET","/billing/paypal/compensation-callback?state=forged",null,null).status());assertEquals(before,bank.captured.get());
    }
    @Test void paidCommandReplaySurvivesProviderOutageWithoutAnotherCharge()throws Exception{
        var f=ready("USD");UUID start=UUID.randomUUID(),confirmation=UUID.randomUUID();
        var initiated=start(f,start);UUID id=UUID.fromString(initiated.body().path("compensationId").asText());
        bank.approve(id,f.execution().fixture().brand().account());assertEquals("PAID",confirm(f,confirmation).body().path("status").asText());
        int charged=bank.captured.get(),sent=bank.sent.get();String order=bank.orderByComp.get(id);bank.unavailable.add(order);bank.offline.set(true);
        try{assertEquals("PAID",start(f,start).body().path("status").asText());assertEquals("PAID",confirm(f,confirmation).body().path("status").asText());}
        finally{bank.offline.set(false);bank.unavailable.remove(order);}
        assertEquals(charged,bank.captured.get());assertEquals(sent,bank.sent.get());
    }
    @Test void differentRecipientCannotBeReportedPaidFromProviderSnapshot()throws Exception{
        var f=ready("USD");UUID id=root(f.execution());bank.payoutStatus.put(id,"UNCLAIMED");pay(f);
        var old=bank.payouts.get(bank.payoutByComp.get(id));bank.payouts.put(old.batchId(),new Payout(old.batchId(),old.itemId(),old.senderBatchId(),old.senderItemId(),
            "OTHER12345FGH","SUCCESS",old.amount(),old.currency(),old.transactionId(),Instant.now()));
        var result=money(f.execution(),f.execution().fixture().creator());assertFalse(result.path("wasPaid").asBoolean());assertEquals("RECONCILIATION_REQUIRED",result.path("status").asText());
    }
    @Test void latePendingProviderObservationDoesNotUndoConfirmedPayment()throws Exception{
        var f=ready("USD");var paid=pay(f);UUID id=UUID.fromString(paid.body().path("compensationId").asText());
        var old=bank.payouts.get(bank.payoutByComp.get(id));bank.payouts.put(old.batchId(),new Payout(old.batchId(),old.itemId(),old.senderBatchId(),old.senderItemId(),
            old.receiver(),"PENDING",old.amount(),old.currency(),old.transactionId(),Instant.now().plusSeconds(1)));
        var result=money(f.execution(),f.execution().fixture().creator());assertEquals("PAID",result.path("status").asText());assertEquals(paid.body().path("paidAt"),result.path("paidAt"));
    }
    @Test void pendingCaptureDoesNotDispatchPayout()throws Exception{
        var f=ready("USD");var initiated=start(f,UUID.randomUUID());UUID id=UUID.fromString(initiated.body().path("compensationId").asText());bank.approve(id,f.execution().fixture().brand().account());
        bank.loseCapture.set(true);confirm(f,UUID.randomUUID());var o=bank.orders.get(bank.orderByComp.get(id));var c=o.captures().getFirst();
        bank.orders.put(o.id(),new Order(o.id(),o.status(),o.compensationId(),o.operationId(),o.payerId(),o.amount(),o.currency(),Instant.now(),
            List.of(new Capture(c.id(),"PENDING",c.amount(),c.currency(),c.time()))));
        int sent=bank.sent.get();compensation.reconcile(id,true);assertEquals(sent,bank.sent.get());assertFalse(money(f.execution(),f.execution().fixture().creator()).path("wasPaid").asBoolean());
        bank.orders.put(o.id(),new Order(o.id(),o.status(),o.compensationId(),o.operationId(),o.payerId(),o.amount(),o.currency(),Instant.now(),o.captures()));
        compensation.reconcile(id,true);assertEquals("PAID",money(f.execution(),f.execution().fixture().creator()).path("status").asText());
    }
    @Test void lostCreationResponseRecoversSameOrder()throws Exception{
        var f=ready("USD");bank.loseOrder.set(true);UUID request=UUID.randomUUID();var first=start(f,request);assertEquals(201,first.status());
        UUID id=UUID.fromString(first.body().path("compensationId").asText());var reference=bank.orderByComp.get(id);
        var retry=start(f,request);assertEquals(201,retry.status());assertEquals(reference,bank.orderByComp.get(id));assertEquals(1,bank.orders.values().stream().filter(o->o.compensationId().equals(id.toString())).count());
    }
    @Test void lostCaptureResponseReconcilesWithoutAnotherCapture()throws Exception{
        var f=ready("USD");var started=start(f,UUID.randomUUID());UUID id=UUID.fromString(started.body().path("compensationId").asText());bank.approve(id,f.execution().fixture().brand().account());
        int before=bank.captured.get();bank.loseCapture.set(true);assertEquals(200,confirm(f,UUID.randomUUID()).status());
        compensation.reconcile(id,true);assertEquals(before+1,bank.captured.get());assertEquals("PAID",money(f.execution(),f.execution().fixture().brand()).path("status").asText());
    }
    @Test void lostPayoutResponseRetriesTheSameBatchAndDoesNotChargeAgain()throws Exception{
        var f=ready("USD");bank.losePayout.set(true);var first=pay(f);UUID id=UUID.fromString(first.body().path("compensationId").asText());String batch=bank.payoutByComp.get(id);int captures=bank.captured.get();
        compensation.reconcile(id,true);assertEquals(batch,bank.payoutByComp.get(id));assertEquals(captures,bank.captured.get());assertEquals("PAID",money(f.execution(),f.execution().fixture().creator()).path("status").asText());
    }
    @Test void wrongPayerCannotBeCharged()throws Exception{
        var f=ready("USD");var started=start(f,UUID.randomUUID());UUID id=UUID.fromString(started.body().path("compensationId").asText());bank.approve(id,UUID.randomUUID().toString());
        int before=bank.captured.get();var result=confirm(f,UUID.randomUUID());assertEquals(409,result.status());assertEquals("PROVIDER_ACCOUNT_MISMATCH",result.body().path("code").asText());assertEquals(before,bank.captured.get());
    }
    @Test void noCreatorForeignUserOrInvalidVersionCanInitiatePayment()throws Exception{
        var f=ready("USD");var e=f.execution();var outsider=user("brands");var value=money(e,e.fixture().brand());
        var body=Map.of("requestId",UUID.randomUUID(),"methodId",f.method(),"client","WEB","expectedVersion",value.path("version").asLong());
        assertEquals(403,call("POST","/collaborations/"+e.collaboration()+"/payment",e.fixture().creator().token(),body).status());
        assertEquals(403,call("GET","/collaborations/"+e.collaboration()+"/compensation",outsider.token(),null).status());
        assertEquals(401,call("GET","/collaborations/"+e.collaboration()+"/compensation",null,null).status());
        assertEquals(409,call("POST","/collaborations/"+e.collaboration()+"/payment",e.fixture().brand().token(),Map.of("requestId",UUID.randomUUID(),"methodId",f.method(),"client","WEB","expectedVersion",9999)).status());
    }
    @RepeatedTest(2) void concurrentStartAndConfirmationDoNotDuplicateMoney()throws Exception{
        var f=ready("USD");var e=f.execution();long version=money(e,e.fixture().brand()).path("version").asLong();UUID key=UUID.randomUUID();var pool=Executors.newFixedThreadPool(3);
        try{
            var body=Map.of("requestId",key,"methodId",f.method(),"client","WEB","expectedVersion",version);
            var results=pool.invokeAll(List.<Callable<Result>>of(()->call("POST","/collaborations/"+e.collaboration()+"/payment",e.fixture().brand().token(),body),()->call("POST","/collaborations/"+e.collaboration()+"/payment",e.fixture().brand().token(),body)));
            var roots=new HashSet<String>();for(var r:results){var response=r.get();assertEquals(201,response.status(),response.body().toString());roots.add(response.body().path("compensationId").asText());}assertEquals(1,roots.size());
            UUID id=UUID.fromString(roots.iterator().next());bank.approve(id,e.fixture().brand().account());
            var current=money(e,e.fixture().brand());var confirmation=Map.of("requestId",UUID.randomUUID(),"expectedVersion",current.path("version").asLong());int before=bank.captured.get();
            var confirmations=pool.invokeAll(List.<Callable<Result>>of(()->call("POST","/collaborations/"+e.collaboration()+"/payment/confirmation",e.fixture().brand().token(),confirmation),()->call("POST","/collaborations/"+e.collaboration()+"/payment/confirmation",e.fixture().brand().token(),confirmation)));
            for(var r:confirmations)assertEquals(200,r.get().status());compensation.reconcile(id,true);assertEquals(before+1,bank.captured.get());assertEquals("PAID",money(e,e.fixture().brand()).path("status").asText());
        }finally{pool.shutdownNow();}
    }
    @Test void historyContainsRealFinancialReferenceAndNoPrivateProviderData()throws Exception{
        var f=ready("USD");pay(f);var e=f.execution();var view=money(e,e.fixture().creator());
        var historical=call("GET","/collaborations/history/"+e.collaboration(),e.fixture().creator().token(),null);
        assertEquals("AVAILABLE",historical.body().path("references").path("compensation").path("availability").asText());assertEquals(view.path("compensationId"),historical.body().path("references").path("compensation").path("resourceId"));
        var audit=call("GET","/collaborations/"+e.collaboration()+"/compensation/history",e.fixture().creator().token(),null);assertEquals(200,audit.status());assertTrue(audit.body().path("total").asInt()>0);
        for(String secret:List.of("encrypted","payerHash","stateHash","senderBatchId","receiver"))assertFalse(view.toString().contains(secret));
    }
}
