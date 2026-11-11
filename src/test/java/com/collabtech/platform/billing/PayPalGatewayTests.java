package com.collabtech.platform.billing;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.collabtech.platform.billing.infrastructure.paypal.*;
import com.collabtech.platform.billing.infrastructure.security.BillingSecrets;
import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.ObjectMapper;
class PayPalGatewayTests {
    final ObjectMapper json=new ObjectMapper();final Instant now=Instant.parse("2026-11-10T12:00:00Z");final UUID authorization=UUID.randomUUID(),account=UUID.randomUUID();
    MockRestServiceServer server;PayPalGateway gateway;
    PayPalProperties settings(boolean enabled,String webhook){return new PayPalProperties(enabled,"FAKE-CLIENT","FAKE-SECRET",webhook,
        "http://localhost:8081/api/v1/billing/paypal/callback","http://localhost:8081/api/v1/billing/paypal/identity-callback","collabpro://billing-return",1800);}
    @BeforeEach void setup(){var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();
        gateway=new PayPalGateway(settings(true,"WH-FAKE"),json,Clock.fixed(now,ZoneOffset.UTC),builder);}
    void token(){server.expect(requestTo(PayPalGateway.API+"/v1/oauth2/token")).andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization","Basic RkFLRS1DTElFTlQ6RkFLRS1TRUNSRVQ="))
        .andExpect(content().string("grant_type=client_credentials"))
        .andRespond(withSuccess("{\"access_token\":\"FAKE-ACCESS\",\"expires_in\":3600}",MediaType.APPLICATION_JSON));}
    String customer(){return "\"customer\":{\"id\":\"CUSTOMER\",\"merchant_customer_id\":\""+account+"\"}";}
    String setupBody(String status,String url){return "{\"id\":\"SETUP\",\"status\":\""+status+"\","+customer()+",\"links\":[{\"rel\":\"approve\",\"href\":\""+url+"\"}]}";}
    @Test void preparesVaultConsentWithoutAnyOrderOrCapture(){
        token();server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens")).andExpect(method(HttpMethod.POST))
            .andExpect(header("PayPal-Request-Id","billing-prepare-"+authorization)).andExpect(header("Authorization","Bearer FAKE-ACCESS"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("merchant_customer_id")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("amount"))))
            .andRespond(withSuccess(setupBody("PAYER_ACTION_REQUIRED","https://www.sandbox.paypal.com/agreements/approve?id=TEST"),MediaType.APPLICATION_JSON));
        var prepared=gateway.prepare(authorization,account,"state");assertEquals("SETUP",prepared.setupId());assertEquals("CUSTOMER",prepared.customerId());server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"https://evil.test/approve","http://sandbox.paypal.com/approve","https://sandbox.paypal.com:444/approve","https://user@sandbox.paypal.com/approve","https://www.paypal.com/approve"})
    void rejectsUntrustedApprovalLinks(String link){
        token();server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens")).andRespond(withSuccess(setupBody("PAYER_ACTION_REQUIRED",link),MediaType.APPLICATION_JSON));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->gateway.prepare(authorization,account,"state")).code());server.verify();
    }
    @Test void verifiesApprovedSetupAndSavesUsingStableIdempotencyHeader(){
        token();server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens/SETUP")).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(setupBody("APPROVED","https://www.sandbox.paypal.com/approve"),MediaType.APPLICATION_JSON));
        server.expect(requestTo(PayPalGateway.API+"/v3/vault/payment-tokens")).andExpect(method(HttpMethod.POST))
            .andExpect(header("PayPal-Request-Id","billing-save-"+authorization))
            .andExpect(content().json("{\"payment_source\":{\"token\":{\"id\":\"SETUP\",\"type\":\"SETUP_TOKEN\"}}}"))
            .andRespond(withSuccess("{\"id\":\"VAULT\","+customer()+",\"payment_source\":{\"paypal\":{\"payer_id\":\"ABCDE12345FGH\",\"email_address\":\"test@example.com\"}}}",MediaType.APPLICATION_JSON));
        assertEquals("VAULT",gateway.completeFunding(authorization,account,"SETUP","CUSTOMER").reference());server.verify();
    }
    @Test void retriesLostVaultResponseEvenWhenSetupIsAlreadyVaulted(){
        token();server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens/SETUP")).andRespond(withSuccess(setupBody("APPROVED","https://www.sandbox.paypal.com/approve"),MediaType.APPLICATION_JSON));
        server.expect(requestTo(PayPalGateway.API+"/v3/vault/payment-tokens")).andExpect(header("PayPal-Request-Id","billing-save-"+authorization))
            .andRespond(withException(new java.net.SocketTimeoutException("response lost")));
        server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens/SETUP")).andRespond(withSuccess(setupBody("VAULTED","https://www.sandbox.paypal.com/approve"),MediaType.APPLICATION_JSON));
        String saved=json.writeValueAsString(Map.of("id","VAULT","customer",Map.of("id","CUSTOMER","merchant_customer_id",account.toString()),
            "payment_source",Map.of("paypal",Map.of("payer_id","ABCDE12345FGH","email_address","test@example.com"))));
        server.expect(requestTo(PayPalGateway.API+"/v3/vault/payment-tokens")).andExpect(header("PayPal-Request-Id","billing-save-"+authorization))
            .andRespond(withSuccess(saved,MediaType.APPLICATION_JSON));
        var error=assertThrows(BillingFailure.class,()->gateway.completeFunding(authorization,account,"SETUP","CUSTOMER"));
        assertEquals(Code.PROVIDER_UNAVAILABLE,error.code());assertTrue(error.retryable());
        assertEquals("VAULT",gateway.completeFunding(authorization,account,"SETUP","CUSTOMER").reference());server.verify();
    }
    @Test void unapprovedConsentNeverCallsPaymentTokenCreation(){
        token();server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens/SETUP")).andRespond(withSuccess(setupBody("PAYER_ACTION_REQUIRED","https://www.sandbox.paypal.com/approve"),MediaType.APPLICATION_JSON));
        var failure=assertThrows(BillingFailure.class,()->gateway.completeFunding(authorization,account,"SETUP","CUSTOMER"));assertEquals(Code.AWAITING_APPROVAL,failure.code());assertTrue(failure.retryable());server.verify();
    }
    @Test void rejectsSetupOwnedByAnotherCustomer(){
        token();server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens/SETUP")).andRespond(withSuccess(setupBody("APPROVED","https://www.sandbox.paypal.com/approve"),MediaType.APPLICATION_JSON));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->gateway.completeFunding(authorization,account,"SETUP","OTHER")).code());server.verify();
    }
    @ParameterizedTest @CsvSource({"403,PROVIDER_PERMISSION_REQUIRED,false","429,PROVIDER_UNAVAILABLE,true","503,PROVIDER_UNAVAILABLE,true","422,PROVIDER_REJECTED,false"})
    void mapsProviderFailuresWithoutLeakingResponse(int status,Code expected,boolean retryable){
        token();server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens")).andRespond(withStatus(HttpStatusCode.valueOf(status)).body("private provider response"));
        var failure=assertThrows(BillingFailure.class,()->gateway.prepare(authorization,account,"state"));assertEquals(expected,failure.code());assertEquals(retryable,failure.retryable());
        assertFalse(failure.getMessage().contains("private"));server.verify();
    }
    @Test void expiresUnauthorizedAppTokenAndRetriesWithSameRequestId(){
        token();server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens")).andExpect(header("PayPal-Request-Id","billing-prepare-"+authorization)).andRespond(withUnauthorizedRequest());
        token();server.expect(requestTo(PayPalGateway.API+"/v3/vault/setup-tokens")).andExpect(header("PayPal-Request-Id","billing-prepare-"+authorization))
            .andRespond(withSuccess(setupBody("PAYER_ACTION_REQUIRED","https://www.sandbox.paypal.com/approve"),MediaType.APPLICATION_JSON));
        assertEquals("SETUP",gateway.prepare(authorization,account,"state").setupId());server.verify();
    }
    @Test void identityConsentRequestsVerifiedIdentityAndFixedReturnUri(){
        String url=gateway.identityApproval("A state");assertTrue(url.startsWith("https://www.sandbox.paypal.com/connect?"));assertTrue(url.contains("state=A+state"));
        assertTrue(url.contains("paypalattributes"));assertFalse(url.contains("FAKE-SECRET"));assertTrue(url.contains("redirect_uri=http"));
    }
    @Test void exchangesIdentityCodeAndReadsVerifiedPayer(){
        server.expect(requestTo(PayPalGateway.API+"/v1/oauth2/token")).andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code&code=CODE")))
            .andRespond(withSuccess("{\"access_token\":\"IDENTITY\",\"expires_in\":3600}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(PayPalGateway.API+"/v1/identity/openidconnect/userinfo?schema=openid")).andExpect(header("Authorization","Bearer IDENTITY"))
            .andRespond(withSuccess("{\"payer_id\":\"ABCDE12345FGH\",\"email\":\"person@example.com\",\"verified_account\":true,\"email_verified\":true}",MediaType.APPLICATION_JSON));
        var access=gateway.exchangeIdentity("CODE");assertEquals(now.plusSeconds(3600),access.expiresAt());assertEquals("ABCDE12345FGH",gateway.identity(access.value()).payerId());server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"{\"email_verified\":true}","{\"verified_account\":true}","{\"verified_account\":false,\"email_verified\":true}"})
    void unverifiedIdentityCannotBeUsedForPayout(String body){
        server.expect(requestTo(PayPalGateway.API+"/v1/identity/openidconnect/userinfo?schema=openid")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertEquals(Code.ACCOUNT_NOT_VERIFIED,assertThrows(BillingFailure.class,()->gateway.identity("ACCESS")).code());server.verify();
    }
    @Test void identityNeverUsesOpaqueSubjectAsPayerId(){
        server.expect(requestTo(PayPalGateway.API+"/v1/identity/openidconnect/userinfo?schema=openid")).andRespond(withSuccess(
            "{\"sub\":\"opaque-account\",\"email\":\"person@example.com\",\"verified_account\":true,\"email_verified\":true}",MediaType.APPLICATION_JSON));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->gateway.identity("ACCESS")).code());server.verify();
    }
    Signature signature(String cert){return new Signature("SHA256withRSA",cert,"TRANSMISSION","SIGNATURE",now.toString());}
    @ParameterizedTest @ValueSource(strings={"SUCCESS","FAILURE"})
    void validatesWebhookSignatureAtFixedProviderEndpoint(String result){
        token();server.expect(requestTo(PayPalGateway.API+"/v1/notifications/verify-webhook-signature")).andExpect(content().string(org.hamcrest.Matchers.containsString("WH-FAKE")))
            .andRespond(withSuccess("{\"verification_status\":\""+result+"\"}",MediaType.APPLICATION_JSON));
        assertEquals("SUCCESS".equals(result),gateway.verify(signature("https://api.paypal.com/v1/notifications/certs/CERT"),Map.of("id","WH-1")));server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"http://api.paypal.com/v1/notifications/certs/CERT","https://evil.test/v1/notifications/certs/CERT",
        "https://api.paypal.com/other","https://api.paypal.com/v1/notifications/certs/CERT?redirect=evil"})
    void webhookCertificateCannotRedirectVerification(String cert){
        assertEquals(Code.SIGNATURE_INVALID,assertThrows(BillingFailure.class,()->gateway.verify(signature(cert),Map.of())).code());server.verify();
    }
    @Test void missingWebhookConfigurationFailsClosed(){
        gateway=new PayPalGateway(settings(true,""),json,Clock.fixed(now,ZoneOffset.UTC),RestClient.builder());
        assertEquals(Code.WEBHOOK_NOT_CONFIGURED,assertThrows(BillingFailure.class,()->gateway.verify(signature("https://api.paypal.com/v1/notifications/certs/CERT"),Map.of())).code());
    }
    @ParameterizedTest @ValueSource(strings={"null","[]","invalid-json"})
    void malformedProviderResponseHasExplicitSafeError(String body){
        server.expect(requestTo(PayPalGateway.API+"/v1/oauth2/token")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertEquals(Code.INVALID_PROVIDER_RESPONSE,assertThrows(BillingFailure.class,()->gateway.prepare(authorization,account,"STATE")).code());server.verify();
    }
    @Test void disabledProviderNeverContactsPayPal(){
        gateway=new PayPalGateway(settings(false,"WH"),json,Clock.fixed(now,ZoneOffset.UTC),RestClient.builder());
        assertEquals(Code.PROVIDER_NOT_CONFIGURED,assertThrows(BillingFailure.class,()->gateway.prepare(authorization,account,"STATE")).code());
    }
    @Test void encryptsWithRandomNonceDetectsTamperingAndFingerprintsCanonicalPayload(){
        var secrets=new BillingSecrets("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",json);
        var one=secrets.encrypt("private-provider-token");var two=secrets.encrypt("private-provider-token");assertNotEquals(one,two);assertEquals("private-provider-token",secrets.decrypt(one));
        var tampered=Base64.getDecoder().decode(one);tampered[15]^=1;assertThrows(IllegalStateException.class,()->secrets.decrypt(Base64.getEncoder().encodeToString(tampered)));
        assertEquals(secrets.fingerprint(Map.of("a",1,"b",List.of(1,2))),secrets.fingerprint(new TreeMap<>(Map.of("b",List.of(1,2),"a",1))));
        assertEquals(43,secrets.random().length());assertNotEquals(secrets.random(),secrets.random());
    }
}
