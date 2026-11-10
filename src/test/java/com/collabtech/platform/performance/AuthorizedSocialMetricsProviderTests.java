package com.collabtech.platform.performance;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.collabtech.platform.performance.infrastructure.providers.AuthorizedSocialMetricsProvider;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import com.collabtech.platform.identity.interfaces.acl.SocialAuthorizationFacade;
import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AuthorizedSocialMetricsProviderTests {
    final Instant now=Instant.parse("2026-01-01T12:00:00Z");
    final UUID creator=UUID.randomUUID(),social=UUID.randomUUID();
    MockRestServiceServer server;AuthorizedSocialMetricsProvider provider;
    IdentityFailure.Code failure;AtomicInteger accessCount=new AtomicInteger();
    @BeforeEach void setup() {
        var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();
        var facade=new SocialAuthorizationFacade(){
            public Status status(UUID c,UUID s,Set<String> scopes){throw new UnsupportedOperationException();}
            public <T>T withAuthorizedAccess(UUID c,UUID s,Set<String> scopes,AuthorizedOperation<T> operation){
                assertEquals(creator,c);assertEquals(social,s);assertEquals(Set.of("video.list"),scopes);accessCount.incrementAndGet();
                if(failure!=null)throw new IdentityFailure(failure);
                return operation.execute(new TokenAccess("test-secret","tiktok"));
            }
        };
        provider=new AuthorizedSocialMetricsProvider(facade,builder.build(),Clock.fixed(now,ZoneOffset.UTC));
    }
    FetchResult fetch(){return provider.fetch(creator,social,"tiktok","123");}
    void json(String body){
        server.expect(requestTo(AuthorizedSocialMetricsProvider.QUERY)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization","Bearer test-secret"))
                .andExpect(content().json("{\"filters\":{\"video_ids\":[\"123\"]}}"))
                .andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
    }
    String video(String values){return "{\"error\":{\"code\":\"ok\"},\"data\":{\"videos\":[{\"id\":\"123\",\"create_time\":1767222000,"+values+"}]}}";}
    @Test void requestsAuthorizedOwnedVideoAndKeepsMissingCountersAbsent() {
        json(video("\"view_count\":40,\"like_count\":0"));
        var result=fetch();assertEquals(FetchStatus.AVAILABLE,result.status());
        assertEquals(Map.of(MetricType.VIEWS,40L,MetricType.LIKES,0L),result.metrics());
        assertEquals(Instant.ofEpochSecond(1767222000L),result.period().start());assertEquals(now,result.period().end());server.verify();
    }
    @Test void noVideosOrMissingCountersReturnNoDataWithoutFabricatedZeros() {
        json("{\"error\":{\"code\":\"ok\"},\"data\":{\"videos\":[]}}");
        assertEquals(FetchStatus.NO_DATA,fetch().status());server.verify();server.reset();
        json(video("\"description\":\"No counters\""));
        assertEquals(FetchStatus.NO_DATA,fetch().status());server.verify();
    }
    @Test void rejectsForeignOrMalformedMeasurements() {
        for(String body:List.of(video("\"view_count\":-1"),video("\"view_count\":1.5"),video("\"view_count\":\"12\""),
                video("\"view_count\":9223372036854775808"),video("\"view_count\":4").replace("\"123\"","\"999\""),
                video("\"view_count\":4").replace("1767222000","999999999999"),"{}")) {
            json(body);var result=fetch();assertEquals(FetchStatus.INVALID_RESPONSE,result.status(),body);
            assertTrue(result.metrics().isEmpty());assertNull(result.period());server.verify();server.reset();
        }
    }
    @Test void providerErrorsDoNotExposeResponseOrCredentials() {
        for(var pair:Map.of(401,FetchStatus.AUTHORIZATION_REQUIRED,403,FetchStatus.INSUFFICIENT_SCOPE,429,FetchStatus.RATE_LIMITED,503,FetchStatus.PROVIDER_UNAVAILABLE).entrySet()) {
            server.expect(requestTo(AuthorizedSocialMetricsProvider.QUERY)).andRespond(withStatus(HttpStatus.valueOf(pair.getKey())).body("test-secret"));
            var result=fetch();assertEquals(pair.getValue(),result.status());assertFalse(result.toString().contains("test-secret"));server.verify();server.reset();
        }
    }
    @Test void authorizationFailuresAndUnsupportedProviderAvoidHttpRequests() {
        failure=IdentityFailure.Code.SOCIAL_SCOPE_REQUIRED;assertEquals(FetchStatus.INSUFFICIENT_SCOPE,fetch().status());
        failure=IdentityFailure.Code.PROVIDER_NOT_CONFIGURED;assertEquals(FetchStatus.PROVIDER_NOT_CONFIGURED,fetch().status());
        failure=IdentityFailure.Code.SOCIAL_ACCOUNT_NOT_FOUND;assertEquals(FetchStatus.AUTHORIZATION_REQUIRED,fetch().status());
        assertEquals(FetchStatus.UNSUPPORTED_PROVIDER,provider.fetch(creator,social,"instagram","123").status());
        assertEquals(3,accessCount.get());server.verify();
    }
    @Test void apiErrorEnvelopeDoesNotProduceMeasurements() {
        json("{\"error\":{\"code\":\"access_token_invalid\",\"message\":\"test-secret\"}}");
        var result=fetch();assertEquals(FetchStatus.AUTHORIZATION_REQUIRED,result.status());assertTrue(result.metrics().isEmpty());server.verify();
    }
}
