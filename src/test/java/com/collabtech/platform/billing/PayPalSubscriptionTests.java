package com.collabtech.platform.billing;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.collabtech.platform.billing.infrastructure.paypal.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.ObjectMapper;
class PayPalSubscriptionTests {
    final ObjectMapper json=new ObjectMapper();final Instant now=Instant.parse("2026-11-11T12:00:00Z");
    final String callback="http://localhost:8081/api/v1/billing/paypal/subscription-callback",base=PayPalGateway.API;
    MockRestServiceServer server;PayPalSubscriptions provider;
    @BeforeEach void setup(){
        var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();
        var settings=new PayPalProperties(true,"FAKE-CLIENT","FAKE-SECRET","WH-FAKE","http://localhost/callback","http://localhost/identity","collabpro://billing-return",1800);
        provider=new PayPalSubscriptions(new PayPalGateway(settings,json,Clock.fixed(now,ZoneOffset.UTC),builder),callback,Clock.fixed(now,ZoneOffset.UTC));
    }
    void token(){server.expect(requestTo(base+"/v1/oauth2/token")).andRespond(withSuccess(json.writeValueAsString(Map.of("access_token","ACCESS","expires_in",3600)),MediaType.APPLICATION_JSON));}
    String plan(String unit,String price,int cycles){
        return json.writeValueAsString(Map.of("id","P-PLAN","status","ACTIVE","quantity_supported",false,"billing_cycles",List.of(Map.of("tenure_type","REGULAR","sequence",1,"total_cycles",cycles,
            "frequency",Map.of("interval_unit",unit,"interval_count",1),"pricing_scheme",Map.of("fixed_price",Map.of("value",price,"currency_code","USD"))))));
    }
    @Test void retriesUseFrozenReturnUrlAcrossDeploymentChanges(){
        token();UUID id=UUID.randomUUID();String original="https://demo.example/api/v1/billing/paypal/subscription-callback";
        server.expect(requestTo(base+"/v1/billing/subscriptions")).andExpect(content().string(org.hamcrest.Matchers.containsString(original+"?state=STATE")))
            .andRespond(withSuccess(json.writeValueAsString(Map.of("id","I-TEST","status","APPROVAL_PENDING","links",List.of(Map.of("rel","approve","href","https://www.sandbox.paypal.com/approve")))),MediaType.APPLICATION_JSON));
        assertEquals("I-TEST",provider.create(id,"P-PLAN","STATE",original).id());server.verify();
    }
    @Test void validatesMonthlyNoTrialPlan(){
        token();server.expect(requestTo(base+"/v1/billing/plans/P-PLAN")).andRespond(withSuccess(plan("MONTH","5",0),MediaType.APPLICATION_JSON));
        var p=provider.plan("P-PLAN");assertTrue(p.supported());assertEquals(0,p.amount().compareTo(new java.math.BigDecimal("5.00")));server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"DAY","WEEK","YEAR"})
    void doesNotOfferUnsupportedIntervals(String unit){
        token();server.expect(requestTo(base+"/v1/billing/plans/P-PLAN")).andRespond(withSuccess(plan(unit,"5",0),MediaType.APPLICATION_JSON));
        assertFalse(provider.plan("P-PLAN").supported());server.verify();
    }
    @Test void createsWithStableIdAndServerOwnedAmount(){
        token();UUID id=UUID.randomUUID();String body=json.writeValueAsString(Map.of("id","I-TEST","status","APPROVAL_PENDING","links",List.of(Map.of("rel","approve","href","https://www.sandbox.paypal.com/approve?id=TEST"))));
        server.expect(requestTo(base+"/v1/billing/subscriptions")).andExpect(header("PayPal-Request-Id",id.toString()))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(id.toString())))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixed_price"))))
            .andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertEquals("I-TEST",provider.create(id,"P-PLAN","STATE").id());server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"http://sandbox.paypal.com/approve","https://evil.test/approve","https://www.paypal.com/approve","https://user@sandbox.paypal.com/approve"})
    void rejectsUntrustedApprovalUrls(String url){
        token();server.expect(requestTo(base+"/v1/billing/subscriptions")).andRespond(withSuccess(json.writeValueAsString(Map.of("id","I-TEST","status","APPROVAL_PENDING","links",List.of(Map.of("rel","approve","href",url)))),MediaType.APPLICATION_JSON));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->provider.create(UUID.randomUUID(),"P-PLAN","STATE")).code());server.verify();
    }
    String snapshot(String status,boolean override){
        var data=new LinkedHashMap<String,Object>();data.put("id","I-TEST");data.put("custom_id","LOCAL");data.put("plan_id","P-PLAN");data.put("status",status);data.put("create_time",now.toString());
        data.put("plan_overridden",override);data.put("subscriber",Map.of("payer_id","ABCDE12345FGH"));
        data.put("billing_info",Map.of("next_billing_time",now.plusSeconds(86400).toString(),"failed_payments_count",0));
        return json.writeValueAsString(data);
    }
    String getPath(){return base+"/v1/billing/subscriptions/I-TEST?fields=plan,last_failed_payment";}
    @Test void pendingRealResponseUsesCreationTimeWithoutRequiringUpdateTime(){
        token();server.expect(requestTo(getPath())).andRespond(withSuccess(snapshot("APPROVAL_PENDING",false),MediaType.APPLICATION_JSON));
        var r=provider.read("I-TEST");assertEquals(now,r.updatedAt());assertTrue(r.charges().isEmpty());server.verify();
    }
    @Test void activeStateMustReadTransactionLedger(){
        token();server.expect(requestTo(getPath())).andRespond(withSuccess(snapshot("ACTIVE",false),MediaType.APPLICATION_JSON));
        var tx=Map.of("id","SALE-1","status","COMPLETED","time",now.minusSeconds(1).toString(),"amount_with_breakdown",Map.of("gross_amount",Map.of("value","5.00","currency_code","USD")));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(base+"/v1/billing/subscriptions/I-TEST/transactions?start_time=")))
            .andRespond(withSuccess(json.writeValueAsString(Map.of("transactions",List.of(tx),"total_pages",1)),MediaType.APPLICATION_JSON));
        var r=provider.read("I-TEST");assertEquals(1,r.charges().size());assertEquals("COMPLETED",r.charges().get(0).status());server.verify();
    }
    @Test void refusesInlinePlanOverrides(){
        token();server.expect(requestTo(getPath())).andRespond(withSuccess(snapshot("ACTIVE",true),MediaType.APPLICATION_JSON));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->provider.read("I-TEST")).code());server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"CANCELLED","EXPIRED","SUSPENDED"})
    void terminalOrSuspendedStatusDoesNotDependOnPaymentEndpoint(String status){
        token();server.expect(requestTo(getPath())).andRespond(withSuccess(snapshot(status,false),MediaType.APPLICATION_JSON));
        assertEquals(status,provider.read("I-TEST").status());server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"https://evil.test/v1/billing/subscriptions/I-TEST/transactions?page=2","https://api-m.sandbox.paypal.com/v1/billing/subscriptions/OTHER/transactions?page=2"})
    void cannotFollowTransactionLinksOutsideTheOwnedResource(String link){
        token();server.expect(requestTo(getPath())).andRespond(withSuccess(snapshot("ACTIVE",false),MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/transactions?start_time="))).andRespond(withSuccess(
            json.writeValueAsString(Map.of("transactions",List.of(),"total_pages",2,"links",List.of(Map.of("rel","next","href",link)))),MediaType.APPLICATION_JSON));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->provider.read("I-TEST")).code());server.verify();
    }
    @Test void followsAllTrustedTransactionPages(){
        token();server.expect(requestTo(getPath())).andRespond(withSuccess(snapshot("ACTIVE",false),MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/transactions?start_time="))).andRespond(withSuccess(
            json.writeValueAsString(Map.of("transactions",List.of(),"total_pages",2,"links",List.of(Map.of("rel","next","href",base+"/v1/billing/subscriptions/I-TEST/transactions?page=2")))),MediaType.APPLICATION_JSON));
        server.expect(requestTo(base+"/v1/billing/subscriptions/I-TEST/transactions?page=2")).andRespond(withSuccess(json.writeValueAsString(Map.of("transactions",List.of(),"total_pages",2)),MediaType.APPLICATION_JSON));
        assertTrue(provider.read("I-TEST").charges().isEmpty());server.verify();
    }
    @Test void cancellationAcceptsEmpty204Response(){
        token();server.expect(requestTo(base+"/v1/billing/subscriptions/I-TEST/cancel")).andExpect(method(HttpMethod.POST)).andRespond(withNoContent());
        assertDoesNotThrow(()->provider.cancel("I-TEST"));server.verify();
    }
}
