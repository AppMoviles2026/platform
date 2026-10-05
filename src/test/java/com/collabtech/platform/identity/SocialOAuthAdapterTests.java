package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.infrastructure.oauth.HttpSocialOAuthClient;
import com.collabtech.platform.identity.infrastructure.security.SecretCipher;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Explicit HTTP fixtures, not evidence of authorization against real social providers. */
@SpringBootTest
@ActiveProfiles("test")
class SocialOAuthAdapterTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired SecretCipher cipher;
    MockRestServiceServer server;
    HttpSocialOAuthClient adapter;
    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var provider = new HttpSocialOAuthClient.Provider("client", "secret", "https://backend.example/callback",
                "https://fixture.example/authorize", "https://fixture.example/token", "https://fixture.example/me", "user.info.basic");
        adapter = new HttpSocialOAuthClient(Map.of("tiktok", provider, "instagram", provider), builder.build(), jdbc, cipher, Clock.systemUTC());
    }
    @Test void tiktokVerifiesIdentityAndEncryptsTokens() {
        server.expect(requestTo("https://fixture.example/token")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_key=client")))
                .andRespond(withSuccess("{\"access_token\":\"access-fixture\",\"refresh_token\":\"refresh-fixture\",\"open_id\":\"id1\",\"scope\":\"user.info.basic\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://fixture.example/me")).andExpect(header("Authorization", "Bearer access-fixture"))
                .andRespond(withSuccess("{\"data\":{\"user\":{\"open_id\":\"id1\",\"display_name\":\"Nombre público\"}},\"error\":{\"code\":\"ok\"}}", MediaType.APPLICATION_JSON));
        var result = adapter.exchangeCode(new SocialPlatform("tiktok"), "code");
        assertEquals("id1", result.externalAccountId());
        var encrypted = jdbc.queryForMap("select encrypted_access_token,encrypted_refresh_token from identity_provider_credentials where credential_id=?", result.credentialReceipt().toString());
        assertEquals("access-fixture", cipher.decrypt((String) encrypted.get("encrypted_access_token")));
        assertEquals("refresh-fixture", cipher.decrypt((String) encrypted.get("encrypted_refresh_token")));
        assertNotEquals("access-fixture", encrypted.get("encrypted_access_token"));
        server.verify();
    }
    @Test void missingGrantedScopeDoesNotCreateCredentials() {
        int before = jdbc.queryForObject("select count(*) from identity_provider_credentials", Integer.class);
        server.expect(requestTo("https://fixture.example/token"))
                .andRespond(withSuccess("{\"access_token\":\"fixture\",\"scope\":\"other\"}", MediaType.APPLICATION_JSON));
        assertEquals(IdentityFailure.Code.AUTHORIZATION_DENIED,
                assertThrows(IdentityFailure.class, () -> adapter.exchangeCode(new SocialPlatform("tiktok"), "code")).code());
        assertEquals(before, jdbc.queryForObject("select count(*) from identity_provider_credentials", Integer.class));
        server.verify();
    }
    @Test void instagramVerifiesIdentityWithMultipartExchange() {
        server.expect(requestTo("https://fixture.example/token"))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith("multipart/form-data")))
                .andRespond(withSuccess("{\"access_token\":\"fixture\",\"user_id\":\"ig1\",\"permissions\":[\"instagram_business_basic\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://fixture.example/me"))
                .andRespond(withSuccess("{\"user_id\":\"ig1\",\"username\":\"creador\"}", MediaType.APPLICATION_JSON));
        assertEquals("creador", adapter.exchangeCode(new SocialPlatform("instagram"), "code").username());
        server.verify();
    }
    @Test void mismatchedIdentityIsDenied() {
        server.expect(requestTo("https://fixture.example/token"))
                .andRespond(withSuccess("{\"access_token\":\"fixture\",\"open_id\":\"first\",\"scope\":\"user.info.basic\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://fixture.example/me"))
                .andRespond(withSuccess("{\"data\":{\"user\":{\"open_id\":\"other\",\"display_name\":\"Name\"}}}", MediaType.APPLICATION_JSON));
        assertThrows(IdentityFailure.class, () -> adapter.exchangeCode(new SocialPlatform("tiktok"), "code"));
        server.verify();
    }
    @Test void unknownProviderIsUnavailableAndAuthorizationIncludesState() {
        assertEquals(IdentityFailure.Code.PROVIDER_NOT_CONFIGURED,
                assertThrows(IdentityFailure.class, () -> adapter.authorizationUri(new SocialPlatform("unknown"), "state")).code());
        assertTrue(adapter.authorizationUri(new SocialPlatform("tiktok"), "state").toString().contains("state=state"));
    }
}
