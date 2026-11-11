package com.collabtech.platform.billing;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.collabtech.platform.billing.infrastructure.paypal.*;
import com.collabtech.platform.billing.application.ports.CompensationPorts.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.ObjectMapper;
class PayPalCompensationTests {
    final ObjectMapper json=new ObjectMapper();final String base=PayPalGateway.API;
    final Instant now=Instant.parse("2026-11-11T12:00:00Z");final UUID op=UUID.randomUUID(),comp=UUID.randomUUID();
    MockRestServiceServer server;PayPalCompensations provider;
    @BeforeEach void setup(){
        var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();
        var properties=new PayPalProperties(true,"FAKE","FAKE","WH-FAKE","http://localhost/callback","http://localhost/identity","collabpro://billing-return",1800);
        var api=new PayPalGateway(properties,json,Clock.fixed(now,ZoneOffset.UTC),builder);
        provider=new PayPalCompensations(api,true,"https://demo.example/callback","USD",18000,2419200,true);
    }
    void token(String scope){server.expect(requestTo(base+"/v1/oauth2/token")).andRespond(withSuccess(json.writeValueAsString(Map.of("access_token","TOKEN","expires_in",3600,"scope",scope)),MediaType.APPLICATION_JSON));}
    void token(){token("https://uri.paypal.com/payments/payouts");}
    Funding funding(){return new Funding(op,comp,new BigDecimal("5.00"),"USD","STATE","https://original.example/callback");}
    String batch(String status){
        return json.writeValueAsString(Map.of("batch_header",Map.of("payout_batch_id","BATCH1","batch_status","SUCCESS","time_created",now.toString(),
            "sender_batch_header",Map.of("sender_batch_id",op.toString())),"items",List.of(
            Map.of("payout_batch_id","BATCH1","payout_item_id","ITEM1","transaction_id","TX1","transaction_status",status,"time_processed",now.toString(),
                "payout_item",Map.of("recipient_type","PAYPAL_ID","receiver","ABCDE12345FGH","sender_item_id",op.toString(),"amount",Map.of("value","5.00","currency","USD"))))));
    }
    @Test void createsFrozenOrderAndSafeReturnWithStableKey(){
        token();
        server.expect(requestTo(base+"/v2/checkout/orders")).andExpect(header("PayPal-Request-Id",op.toString()))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("https://original.example/callback?state=STATE")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(comp.toString())))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"value\":\"5.00\"")))
            .andRespond(withSuccess("{\"id\":\"ORDER1\",\"links\":[{\"rel\":\"payer-action\",\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=ORDER1\"}]}",MediaType.APPLICATION_JSON));
        assertEquals("ORDER1",provider.createOrder(funding()).id());server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"https://www.paypal.com/approve","http://sandbox.paypal.com/approve","https://evil.example/approve","https://user@sandbox.paypal.com/approve"})
    void rejectsUntrustedApproval(String link){
        token();server.expect(requestTo(base+"/v2/checkout/orders")).andRespond(withSuccess(json.writeValueAsString(Map.of("id","ORDER1","links",List.of(Map.of("rel","approve","href",link)))),MediaType.APPLICATION_JSON));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->provider.createOrder(funding())).code());server.verify();
    }
    @Test void captureUsesDifferentStableKeyAndAcceptsEmptyResponse(){
        token();String key=UUID.nameUUIDFromBytes(("CAPTURE:"+op).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        server.expect(requestTo(base+"/v2/checkout/orders/ORDER1/capture")).andExpect(header("PayPal-Request-Id",key)).andRespond(withNoContent());
        provider.capture("ORDER1",op);assertNotEquals(op.toString(),key);server.verify();
    }
    @Test void orderReadsGrossCaptureAndIdentityInsteadOfClientFlags(){
        token();var unit=Map.of("reference_id",op.toString(),"custom_id",comp.toString(),"amount",Map.of("value","5.00","currency_code","USD"),
            "payments",Map.of("captures",List.of(Map.of("id","CAP1","status","COMPLETED","create_time",now.toString(),"amount",Map.of("value","5.00","currency_code","USD")))));
        server.expect(requestTo(base+"/v2/checkout/orders/ORDER1")).andRespond(withSuccess(json.writeValueAsString(Map.of("id","ORDER1","intent","CAPTURE","status","COMPLETED","create_time",now.toString(),"payer",Map.of("payer_id","ABCDE12345FGH"),"purchase_units",List.of(unit))),MediaType.APPLICATION_JSON));
        var order=provider.readOrder("ORDER1");assertEquals(comp.toString(),order.compensationId());assertEquals(now,order.captures().getFirst().time());assertEquals("ABCDE12345FGH",order.payerId());server.verify();
    }
    @Test void payoutUsesVerifiedIdAndNeverBatchStatusForIndividualSuccess(){
        token();
        server.expect(requestTo(base+"/v1/payments/payouts")).andExpect(header("PayPal-Request-Id",op.toString()))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"recipient_type\":\"PAYPAL_ID\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"sender_batch_id\":\""+op+"\"")))
            .andRespond(withSuccess("{\"batch_header\":{\"payout_batch_id\":\"BATCH1\"}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(base+"/v1/payments/payouts/BATCH1?page_size=1000")).andRespond(withSuccess(batch("UNCLAIMED"),MediaType.APPLICATION_JSON));
        var result=provider.createPayout(op,comp,"ABCDE12345FGH",new BigDecimal("5.00"),"USD");assertEquals("UNCLAIMED",result.status());assertEquals("ABCDE12345FGH",result.receiver());server.verify();
    }
    @Test void duplicateBatchRecoversOriginalBeforeReadingActualItem(){
        token();String duplicate=json.writeValueAsString(Map.of("name","DUPLICATE_BATCH_ID","links",List.of(Map.of("href",base+"/v1/payments/payouts/BATCH1","rel","self"))));
        server.expect(requestTo(base+"/v1/payments/payouts")).andRespond(withStatus(HttpStatus.BAD_REQUEST).body(duplicate).contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(base+"/v1/payments/payouts/BATCH1?page_size=1000")).andRespond(withSuccess(batch("SUCCESS"),MediaType.APPLICATION_JSON));
        server.expect(requestTo(base+"/v1/payments/payouts/BATCH1?page_size=1000")).andRespond(withSuccess(batch("SUCCESS"),MediaType.APPLICATION_JSON));
        assertEquals("SUCCESS",provider.createPayout(op,comp,"ABCDE12345FGH",new BigDecimal("5.00"),"USD").status());server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"https://evil.example/v1/payments/payouts/BATCH1","https://api-m.paypal.com/v1/payments/payouts/BATCH1"})
    void duplicateRecoveryCannotFollowForeignHosts(String url){
        token();server.expect(requestTo(base+"/v1/payments/payouts")).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
            .body(json.writeValueAsString(Map.of("name","DUPLICATE_BATCH_ID","links",List.of(Map.of("href",url))))));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->provider.createPayout(op,comp,"ABCDE12345FGH",new BigDecimal("5.00"),"USD")).code());server.verify();
    }
    @Test void expiredBearerRetriesSamePayoutIdentityOnlyOnce(){
        token();server.expect(requestTo(base+"/v1/payments/payouts")).andExpect(header("PayPal-Request-Id",op.toString())).andRespond(withUnauthorizedRequest());
        token();server.expect(requestTo(base+"/v1/payments/payouts")).andExpect(header("PayPal-Request-Id",op.toString())).andRespond(withSuccess("{\"batch_header\":{\"payout_batch_id\":\"BATCH1\"}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(base+"/v1/payments/payouts/BATCH1?page_size=1000")).andRespond(withSuccess(batch("SUCCESS"),MediaType.APPLICATION_JSON));
        assertEquals("SUCCESS",provider.createPayout(op,comp,"ABCDE12345FGH",new BigDecimal("5.00"),"USD").status());server.verify();
    }
    @Test void missingPayoutPermissionBlocksBeforeAnyFinancialWrite(){
        token("openid");assertEquals(Code.PROVIDER_PERMISSION_REQUIRED,assertThrows(BillingFailure.class,provider::preflight).code());server.verify();
    }
    @Test void invalidRecipientDoesNotCallNetwork(){
        assertThrows(BillingFailure.class,()->provider.createPayout(op,comp,"arbitrary@example.com",new BigDecimal("5.00"),"USD"));server.verify();
    }
    @Test void multipleItemsCannotBeMistakenForOneCompensation(){
        token();var body=json.readTree(batch("SUCCESS"));((tools.jackson.databind.node.ArrayNode)body.path("items")).add(body.path("items").get(0));
        server.expect(requestTo(base+"/v1/payments/payouts/BATCH1?page_size=1000")).andRespond(withSuccess(body.toString(),MediaType.APPLICATION_JSON));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->provider.readPayout("BATCH1")).code());server.verify();
    }
}
