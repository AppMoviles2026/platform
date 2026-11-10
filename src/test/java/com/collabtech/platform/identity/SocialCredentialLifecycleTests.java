package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.collabtech.platform.identity.application.commands.RegisterCreatorCommand;
import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import com.collabtech.platform.identity.application.handlers.RegisterCreatorCommandHandler;
import com.collabtech.platform.identity.domain.model.valueobjects.*;
import com.collabtech.platform.identity.infrastructure.acl.SocialAuthorizationFacadeAdapter;
import com.collabtech.platform.identity.infrastructure.oauth.*;
import com.collabtech.platform.identity.infrastructure.security.SecretCipher;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

@SpringBootTest
@ActiveProfiles("test")
class SocialCredentialLifecycleTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired SecretCipher cipher;
    @Autowired PlatformTransactionManager manager;
    @Autowired RegisterCreatorCommandHandler creators;
    MockRestServiceServer server;
    DatabaseSocialAuthorizationAccess access;
    SocialAuthorizationFacadeAdapter facade;
    HttpSocialOAuthClient oauth;
    final Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
    @BeforeEach void setup() {
        var builder=RestClient.builder(); server=MockRestServiceServer.bindTo(builder).build();
        var provider=new HttpSocialOAuthClient.Provider("client","secret","https://backend.example/callback",
                "https://fixture.example/auth","https://fixture.example/token","https://fixture.example/me","user.info.basic");
        var providers=Map.of("tiktok",provider,"instagram",provider); var http=builder.build();
        access=new DatabaseSocialAuthorizationAccess(jdbc,cipher,clock,new HttpSocialTokenRefresher(providers,http,clock),manager);
        facade=new SocialAuthorizationFacadeAdapter(access); oauth=new HttpSocialOAuthClient(providers,http,jdbc,cipher,clock);
    }
    @Test void activeCredentialsAreScopedOwnerCheckedAndSecretsAreRedacted() {
        var f=fixture("tiktok",false,true); var state=inspect(f,Set.of("video.list"));
        assertEquals("READY",state.status()); assertTrue(state.grantedScopes().contains("video.list"));
        var outcome=facade.withAuthorizedAccess(f.owner(),f.social(),Set.of("video.list"), token -> {
            assertEquals("old-access",token.accessToken()); assertFalse(token.toString().contains("old-access")); return "received";
        });
        assertEquals("received",outcome);
        assertFailure(IdentityFailure.Code.SOCIAL_ACCOUNT_NOT_FOUND,()->facade.status(UUID.randomUUID(),f.social(),Set.of()));
        assertFailure(IdentityFailure.Code.SOCIAL_SCOPE_REQUIRED,()->ensure(f,Set.of("missing.permission")));
        assertEquals("INSUFFICIENT_SCOPE",inspect(f,Set.of("missing.permission")).status()); server.verify();
    }
    @Test void expiredTikTokCredentialIsRotatedOnceAndPersistedEncrypted() {
        var f=fixture("tiktok",true,true); expectRefresh("new-access","new-refresh",f.external());
        assertEquals("EXPIRED",inspect(f,Set.of()).status()); assertEquals("READY",ensure(f,Set.of("video.list")).status());
        var row=jdbc.queryForMap("select * from identity_provider_credentials where social_id=?",f.social().toString());
        assertEquals("new-access",cipher.decrypt((String)row.get("encrypted_access_token")));
        assertEquals("new-refresh",cipher.decrypt((String)row.get("encrypted_refresh_token")));
        assertNotEquals("new-access",row.get("encrypted_access_token"));
        assertEquals(now.plusSeconds(600),((Timestamp)row.get("expires_at")).toInstant());
        assertEquals(now.plusSeconds(6000),jdbc.queryForObject("select refresh_expires_at from identity_provider_credentials where social_id=?",Timestamp.class,f.social().toString()).toInstant());
        assertEquals("READY",ensure(f,Set.of()).status()); server.verify();
    }
    @Test void concurrentRenewalsDoNotReuseTheRotatingRefreshToken() throws Exception {
        var f=fixture("tiktok",true,true); expectRefresh("new-access","new-refresh",f.external()); var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> task=()->{ gate.await(5,TimeUnit.SECONDS); return ensure(f,Set.of()).status(); };
            var a=pool.submit(task); var b=pool.submit(task); gate.countDown();
            assertEquals("READY",a.get(15,TimeUnit.SECONDS)); assertEquals("READY",b.get(15,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("select version from identity_provider_credentials where social_id=?",Integer.class,f.social().toString())); server.verify();
    }
    @Test void renewedTokenIsVisibleEvenWhenCallerAlreadyHasAnOlderRepeatableReadSnapshot() {
        var f=fixture("tiktok",true,true); expectRefresh("new-access","new-refresh",f.external());
        var outer=new TransactionTemplate(manager); outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        outer.executeWithoutResult(tx->{
            jdbc.queryForObject("select encrypted_access_token from identity_provider_credentials where social_id=?",String.class,f.social().toString());
            assertEquals("new-access",facade.withAuthorizedAccess(f.owner(),f.social(),Set.of(),token->token.accessToken()));
        });
        server.verify();
    }
    @Test void rejectedRefreshPersistsReauthorizationEvenWhenTheCallerFails() {
        var f=fixture("tiktok",true,true);
        server.expect(requestTo("https://fixture.example/token")).andRespond(withBadRequest().body("{\"error\":\"invalid_grant\",\"error_description\":\"secret-provider-detail\"}").contentType(MediaType.APPLICATION_JSON));
        var tx=new TransactionTemplate(manager);
        var error=assertThrows(IdentityFailure.class,()->tx.execute(status->{ensure(f,Set.of()); return null;}));
        assertEquals(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED,error.code()); assertFalse(error.toString().contains("secret-provider-detail"));
        assertEquals("REAUTHORIZATION_REQUIRED",inspect(f,Set.of()).status());
        assertFailure(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED,()->ensure(f,Set.of())); server.verify();
    }
    @Test void temporaryProviderFailureDoesNotDestroyCredentialsAndCanBeRetried() {
        var f=fixture("tiktok",true,true);
        server.expect(requestTo("https://fixture.example/token")).andRespond(withServerError());
        expectRefresh("retry-access","retry-refresh",f.external());
        assertFailure(IdentityFailure.Code.PROVIDER_FAILED,()->ensure(f,Set.of()));
        assertEquals("EXPIRED",inspect(f,Set.of()).status());
        assertEquals("READY",ensure(f,Set.of()).status()); server.verify();
    }
    @Test void invalidClientIsNotMisreportedAsUserRevocation() {
        var f=fixture("tiktok",true,true);
        server.expect(requestTo("https://fixture.example/token")).andRespond(withBadRequest().body("{\"error\":\"invalid_client\"}").contentType(MediaType.APPLICATION_JSON));
        assertFailure(IdentityFailure.Code.PROVIDER_FAILED,()->ensure(f,Set.of()));
        assertEquals("EXPIRED",inspect(f,Set.of()).status()); server.verify();
    }
    @Test void refreshCannotSwitchTheVerifiedExternalIdentity() {
        var f=fixture("tiktok",true,true); expectRefresh("other-access","other-refresh","different-identity");
        assertFailure(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED,()->ensure(f,Set.of()));
        assertEquals("old-access",cipher.decrypt(jdbc.queryForObject("select encrypted_access_token from identity_provider_credentials where social_id=?",String.class,f.social().toString())));
        server.verify();
    }
    @Test void expiredRefreshAndUnsupportedRefreshRequireConsentWithoutCallingProvider() {
        var expired=fixture("tiktok",true,true);
        jdbc.update("update identity_provider_credentials set refresh_expires_at=? where social_id=?",Timestamp.from(now),expired.social().toString());
        assertFailure(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED,()->ensure(expired,Set.of()));
        var instagram=fixture("instagram",true,false);
        assertEquals("REAUTHORIZATION_REQUIRED",inspect(instagram,Set.of()).status());
        assertFailure(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED,()->ensure(instagram,Set.of())); server.verify();
    }
    @Test void legacyInstagramPermissionsAndRevokedOrMissingAssociationsHaveExplicitStates() {
        var f=fixture("instagram",false,false);
        jdbc.update("update identity_provider_credentials set scopes=? where social_id=?","[\"instagram_business_basic\"]",f.social().toString());
        assertEquals("READY",inspect(f,Set.of("instagram_business_basic")).status());
        jdbc.update("update identity_social_account set status='REVOKED' where social_id=?",f.social().toString());
        assertEquals("REVOKED",inspect(f,Set.of()).status()); assertFailure(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED,()->ensure(f,Set.of()));
        jdbc.update("delete from identity_provider_credentials where social_id=?",f.social().toString());
        jdbc.update("update identity_social_account set status='ACTIVE' where social_id=?",f.social().toString());
        assertEquals("MISSING_CREDENTIALS",inspect(f,Set.of()).status());
        jdbc.update("update identity_account set status='SUSPENDED' where account_id=(select account_id from identity_creator_profile where profile_id=?)",f.owner().toString());
        assertFailure(IdentityFailure.Code.SOCIAL_ACCOUNT_NOT_FOUND,()->inspect(f,Set.of())); server.verify();
    }
    @Test void missingProviderConfigurationIsExplicit() {
        var f=fixture("tiktok",false,true); var empty=new HttpSocialTokenRefresher(Map.of(),RestClient.create(),clock);
        var service=new DatabaseSocialAuthorizationAccess(jdbc,cipher,clock,empty,manager);
        assertEquals("PROVIDER_NOT_CONFIGURED",service.inspect(new CreatorProfileId(f.owner()),new SocialMediaAccountId(f.social()),Set.of()).status());
        assertFailure(IdentityFailure.Code.PROVIDER_NOT_CONFIGURED,()->service.ensureUsable(new CreatorProfileId(f.owner()),new SocialMediaAccountId(f.social()),Set.of()));
    }
    @Test void newConsentReplacesExpiredCredentialsWithoutDuplicatingTheSocialIdentity() {
        var f=fixture("tiktok",true,true); expectExchange(f.external(),"fresh-access");
        new TransactionTemplate(manager).executeWithoutResult(status->{
            var fresh=oauth.exchangeCode(new SocialPlatform("tiktok"),"fresh-code");
            assertTrue(oauth.replaceUnavailableCredentials(fresh.credentialReceipt(),new SocialMediaAccountId(f.social())));
        });
        assertEquals(1,jdbc.queryForObject("select count(*) from identity_social_account where social_id=?",Integer.class,f.social().toString()));
        assertEquals(1,jdbc.queryForObject("select count(*) from identity_provider_credentials where social_id=?",Integer.class,f.social().toString()));
        assertEquals("fresh-access",cipher.decrypt(jdbc.queryForObject("select encrypted_access_token from identity_provider_credentials where social_id=?",String.class,f.social().toString())));
        assertEquals("READY",inspect(f,Set.of()).status()); server.verify();
    }
    @Test void validDuplicateConsentDoesNotReplaceCredentialsAndTransactionRollsBackReceipt() {
        var f=fixture("tiktok",false,true); expectExchange(f.external(),"duplicate-access");
        int before=jdbc.queryForObject("select count(*) from identity_provider_credentials",Integer.class);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).executeWithoutResult(status->{
            var fresh=oauth.exchangeCode(new SocialPlatform("tiktok"),"duplicate");
            assertFalse(oauth.replaceUnavailableCredentials(fresh.credentialReceipt(),new SocialMediaAccountId(f.social())));
            throw new IllegalStateException("Duplicate association");
        }));
        assertEquals(before,jdbc.queryForObject("select count(*) from identity_provider_credentials",Integer.class));
        assertEquals("old-access",cipher.decrypt(jdbc.queryForObject("select encrypted_access_token from identity_provider_credentials where social_id=?",String.class,f.social().toString())));
        server.verify();
    }

    @Test void performanceAdapterRenewsExpiredCredentialsAndUsesOnlyTheNewToken() {
        var f=fixture("tiktok",true,true);expectRefresh("renewed-performance-token","renewed-refresh",f.external());
        var builder=RestClient.builder();var metricsHttp=MockRestServiceServer.bindTo(builder).build();
        metricsHttp.expect(requestTo(com.collabtech.platform.performance.infrastructure.providers.AuthorizedSocialMetricsProvider.QUERY))
                .andExpect(header("Authorization","Bearer renewed-performance-token"))
                .andRespond(withSuccess("{\"error\":{\"code\":\"ok\"},\"data\":{\"videos\":[{\"id\":\"123\",\"create_time\":1,\"view_count\":25}]}}",MediaType.APPLICATION_JSON));
        var metrics=new com.collabtech.platform.performance.infrastructure.providers.AuthorizedSocialMetricsProvider(facade,builder.build(),clock);
        var result=metrics.fetch(f.owner(),f.social(),"tiktok","123");
        assertEquals(com.collabtech.platform.performance.domain.model.PerformanceModels.FetchStatus.AVAILABLE,result.status());
        assertEquals(25L,result.metrics().get(com.collabtech.platform.performance.domain.model.PerformanceModels.MetricType.VIEWS));
        assertFalse(result.toString().contains("renewed-performance-token"));server.verify();metricsHttp.verify();
    }
    @Test void performanceAdapterDoesNotQueryVideosWithoutScopeOrRenewableConsent() {
        var f=fixture("tiktok",false,false);
        var builder=RestClient.builder();var metricsHttp=MockRestServiceServer.bindTo(builder).build();
        var metrics=new com.collabtech.platform.performance.infrastructure.providers.AuthorizedSocialMetricsProvider(facade,builder.build(),clock);
        jdbc.update("update identity_provider_credentials set scopes='user.info.basic' where social_id=?",f.social().toString());
        assertEquals(com.collabtech.platform.performance.domain.model.PerformanceModels.FetchStatus.INSUFFICIENT_SCOPE,metrics.fetch(f.owner(),f.social(),"tiktok","123").status());
        jdbc.update("update identity_provider_credentials set scopes='video.list',expires_at=? where social_id=?",Timestamp.from(now.minusSeconds(1)),f.social().toString());
        assertEquals(com.collabtech.platform.performance.domain.model.PerformanceModels.FetchStatus.AUTHORIZATION_REQUIRED,metrics.fetch(f.owner(),f.social(),"tiktok","123").status());
        server.verify();metricsHttp.verify();
    }

    private com.collabtech.platform.identity.application.projections.IdentityViews.SocialAccessView inspect(Fixture f,Set<String> scopes) {
        return access.inspect(new CreatorProfileId(f.owner()),new SocialMediaAccountId(f.social()),scopes);
    }
    private com.collabtech.platform.identity.application.projections.IdentityViews.SocialAccessView ensure(Fixture f,Set<String> scopes) {
        return access.ensureUsable(new CreatorProfileId(f.owner()),new SocialMediaAccountId(f.social()),scopes);
    }
    private void assertFailure(IdentityFailure.Code code,Runnable task) { assertEquals(code,assertThrows(IdentityFailure.class,task::run).code()); }
    private void expectRefresh(String token,String refresh,String identity) {
        server.expect(requestTo("https://fixture.example/token")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("{\"access_token\":\""+token+"\",\"refresh_token\":\""+refresh+"\",\"open_id\":\""+identity
                        +"\",\"expires_in\":600,\"refresh_expires_in\":6000,\"scope\":\"user.info.basic,video.list\"}",MediaType.APPLICATION_JSON));
    }
    private void expectExchange(String identity,String token) {
        server.expect(requestTo("https://fixture.example/token")).andRespond(withSuccess("{\"access_token\":\""+token+"\",\"open_id\":\""+identity
                +"\",\"scope\":\"user.info.basic,video.list\",\"expires_in\":600}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://fixture.example/me")).andRespond(withSuccess("{\"data\":{\"user\":{\"open_id\":\""+identity+"\",\"display_name\":\"User\"}}}",MediaType.APPLICATION_JSON));
    }
    private Fixture fixture(String platform,boolean expired,boolean refresh) {
        var account=creators.handle(new RegisterCreatorCommand("Social test",new EmailAddress("social-"+UUID.randomUUID()+"@example.com"),"SocialPassword123!"));
        UUID social=UUID.randomUUID(); String external=UUID.randomUUID().toString();
        jdbc.update("insert into identity_social_account(social_id,profile_id,platform,external_account_id,username,status) values(?,?,?,?,?,'ACTIVE')",
                social.toString(),account.profileId().toString(),platform,external,"social-user");
        jdbc.update("insert into identity_provider_credentials(credential_id,social_id,encrypted_access_token,encrypted_refresh_token,scopes,expires_at,refresh_expires_at) values(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),social.toString(),cipher.encrypt("old-access"),refresh?cipher.encrypt("old-refresh"):null,
                "user.info.basic,video.list",Timestamp.from(expired?now.minusSeconds(1):now.plusSeconds(600)),refresh?Timestamp.from(now.plusSeconds(6000)):null);
        return new Fixture(account.profileId(),social,external);
    }
    record Fixture(UUID owner,UUID social,String external) {}
}
