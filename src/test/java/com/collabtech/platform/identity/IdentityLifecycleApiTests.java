package com.collabtech.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.collabtech.platform.identity.application.ports.SocialOAuthClient;
import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import com.collabtech.platform.identity.infrastructure.security.SecretCipher;
import com.collabtech.platform.identity.infrastructure.security.SecureTokens;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdentityLifecycleApiTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecretCipher cipher;
    @LocalServerPort int port;
    @MockitoBean SocialOAuthClient social;
    private MockMvc mvc;
    private static final String PASSWORD = "MiClaveOriginal123!";

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(social.authorizationUri(any(), anyString())).thenAnswer(call ->
                URI.create("https://oauth.fixture.test/authorize?state=" + call.getArgument(1)));
        when(social.exchangeCode(any(), eq("approved"))).thenReturn(new SocialOAuthClient.AuthorizedAccount("external-id", "creator-name", UUID.randomUUID()));
    }
    @ParameterizedTest @ValueSource(strings={"brands", "creators"})
    void loginReturnsServerRoleAndHashedExpiringToken(String route) throws Exception {
        String email = register(route);
        String body = login(email, PASSWORD);
        var session = json.readTree(body);
        assertEquals(route.equals("brands") ? "BRAND" : "CREATOR", session.path("account").path("accountType").asText());
        String token = session.path("accessToken").asText();
        assertTrue(token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
        var claims = json.readTree(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertEquals(session.path("account").path("accountId").asText(), claims.path("sub").asText());
        assertEquals(session.path("account").path("accountType").asText(), claims.path("role").asText());
        assertEquals("collabpro-platform", claims.path("iss").asText());
        assertEquals("collabpro-clients", claims.path("aud").isArray() ? claims.path("aud").get(0).asText() : claims.path("aud").asText());
        assertFalse(claims.has("email"));
        assertTrue(Instant.parse(session.path("expiresAt").asText()).isAfter(Instant.now()));
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_access_session where token_hash=?",
                Integer.class, SecureTokens.digest(token)));
        assertFalse(body.contains("passwordHash"));
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Usuario"));
    }
    @Test void invalidCredentialsUnknownAccountsAndInactiveAccountsAreDenied() throws Exception {
        String email = register("creators");
        String unknown = "missing-" + UUID.randomUUID() + "@example.com";
        String response = failedLogin(email, "WrongPassword");
        assertEquals(response, failedLogin(unknown, "WrongPassword"));
        jdbc.update("update identity_account set status='SUSPENDED' where email=?", email);
        assertEquals(response, failedLogin(email, PASSWORD));
    }
    @Test void protectedEndpointsRejectMissingInvalidAndExpiredSessions() throws Exception {
        mvc.perform(get("/api/v1/profiles/me/creator")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profiles/me/creator").header("Authorization", "Bearer forged")).andExpect(status().isUnauthorized());
        String token = token(register("creators"));
        jdbc.update("update identity_access_session set expires_at=? where token_hash=?",
                Timestamp.from(Instant.now().minusSeconds(5)), SecureTokens.digest(token));
        mvc.perform(get("/api/v1/profiles/me/creator").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
    @Test void creatorProfileIsPersistedOnFirstSaveAndCanBeUpdatedWithoutChangingOtherAccounts() throws Exception {
        String first = register("creators"), second = register("creators");
        String token = token(first), secondToken = token(second);
        String profile = "{\"displayName\":\"Nuevo nombre\",\"biography\":\"Contenido local\",\"niche\":\"Gastronomía\","
                + "\"audienceDescription\":\"Jóvenes de Lima\",\"location\":\"Lima\"}";
        mvc.perform(put("/api/v1/profiles/me/creator").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(profile)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/profiles/me/creator").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.displayName").value("Nuevo nombre")).andExpect(jsonPath("$.audienceDescription").value("Jóvenes de Lima"));
        mvc.perform(get("/api/v1/profiles/me/creator").header("Authorization", "Bearer " + secondToken))
                .andExpect(jsonPath("$.displayName").value("Usuario"));
        mvc.perform(put("/api/v1/profiles/me/creator").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(profile.replace("Nuevo nombre", "Actualizado")))
                .andExpect(jsonPath("$.displayName").value("Actualizado"));
    }
    @Test void companyCannotEditCreatorProfileAndInputsCannotChangeAccountIdsOrRoles() throws Exception {
        String brandToken = token(register("brands"));
        mvc.perform(get("/api/v1/profiles/me/creator").header("Authorization", "Bearer " + brandToken)).andExpect(status().isForbidden());
        String creatorToken = token(register("creators"));
        mvc.perform(put("/api/v1/profiles/me/creator").header("Authorization", "Bearer " + creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/profiles/me/creator").header("Authorization", "Bearer " + creatorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Nombre\",\"accountId\":\"other\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void recoveryUsesGenericResponseAndStoresNoPlaintextToken() throws Exception {
        String email = register("creators");
        String response = recovery(email);
        assertEquals(response, recovery("missing-" + UUID.randomUUID() + "@example.com"));
        assertFalse(response.contains("token"));
        String encrypted = jdbc.queryForObject("select encrypted_token from identity_recovery_mail where email=? and sent_at is null", String.class, email);
        String raw = cipher.decrypt(encrypted);
        assertFalse(encrypted.contains(raw));
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_recovery_token where token_hash=?", Integer.class, SecureTokens.digest(raw)));
    }
    @Test void resetPasswordIsSingleUseAndRevokesPreviousSessions() throws Exception {
        String email = register("creators");
        String oldToken = token(email);
        recovery(email);
        String raw = cipher.decrypt(jdbc.queryForObject("select encrypted_token from identity_recovery_mail where email=? and sent_at is null", String.class, email));
        String body = "{\"token\":\"" + raw + "\",\"newPassword\":\"MiClaveNueva123!\"}";
        mvc.perform(post("/api/v1/auth/password-resets").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/password-resets").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isUnauthorized());
        failedLogin(email, PASSWORD);
        assertFalse(login(email, "MiClaveNueva123!").isBlank());
    }
    @Test void expiredRecoveryTokenIsRejectedWithoutChangingPassword() throws Exception {
        String email = register("creators"); recovery(email);
        String raw = cipher.decrypt(jdbc.queryForObject("select encrypted_token from identity_recovery_mail where email=? and sent_at is null", String.class, email));
        jdbc.update("update identity_recovery_token set expires_at=? where token_hash=?", Timestamp.from(Instant.now().minusSeconds(1)), SecureTokens.digest(raw));
        mvc.perform(post("/api/v1/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"MiClaveNueva123!\"}")).andExpect(status().isBadRequest());
        login(email, PASSWORD);
    }
    @Test void replacingRecoveryInvalidatesThePreviousToken() throws Exception {
        String email = register("creators"); recovery(email);
        String old = cipher.decrypt(jdbc.queryForObject("select encrypted_token from identity_recovery_mail where email=? and sent_at is null", String.class, email));
        recovery(email);
        mvc.perform(post("/api/v1/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + old + "\",\"newPassword\":\"MiClaveNueva123!\"}")).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_recovery_token t join identity_account a on t.account_id=a.account_id where a.email=?", Integer.class, email));
    }
    @Test void concurrentLoginCannotKeepAnOldPasswordSessionAfterReset() throws Exception {
        String email = register("creators"); recovery(email);
        String raw = cipher.decrypt(jdbc.queryForObject("select encrypted_token from identity_recovery_mail where email=? and sent_at is null", String.class, email));
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var login = workers.submit(() -> {
                ready.countDown(); start.await();
                return mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse();
            });
            var reset = workers.submit(() -> {
                ready.countDown(); start.await();
                return mvc.perform(post("/api/v1/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\",\"newPassword\":\"MiClaveNueva123!\"}")).andReturn().getResponse();
            });
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            assertEquals(204, reset.get(20, java.util.concurrent.TimeUnit.SECONDS).getStatus());
            var result = login.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(result.getStatus() == 200 || result.getStatus() == 401);
            if (result.getStatus() == 200) {
                String old = json.readTree(result.getContentAsString()).path("accessToken").asText();
                mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + old)).andExpect(status().isUnauthorized());
            }
        } finally { start.countDown(); workers.shutdownNow(); }
    }
    @Test void authorizedSocialAccountIsLinkedOnlyToStateOwnerAndRejectsDuplicate() throws Exception {
        String token = token(register("creators"));
        String state = authorize(token, "tiktok");
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state", state).param("code", "approved"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("creator-name"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        mvc.perform(get("/api/v1/social-accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].platform").value("tiktok"));
        String second = authorize(token, "tiktok");
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state", second).param("code", "approved"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state", state).param("code", "approved"))
                .andExpect(status().isBadRequest());
    }
    @Test void rejectionConsumesStateWithoutLinkingOrCallingProvider() throws Exception {
        String token = token(register("creators"));
        String state = authorize(token, "instagram");
        mvc.perform(get("/api/v1/social-accounts/instagram/callback").param("state", state).param("error", "access_denied"))
                .andExpect(status().isForbidden());
        verify(social, never()).exchangeCode(any(), any());
        mvc.perform(get("/api/v1/social-accounts/me").header("Authorization", "Bearer " + token)).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/social-accounts/instagram/callback").param("state", state).param("code", "approved"))
                .andExpect(status().isBadRequest());
    }
    @Test void invalidExpiredOrWrongPlatformStateDoesNotCallProvider() throws Exception {
        String token = token(register("creators"));
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state", "forged").param("code", "approved"))
                .andExpect(status().isBadRequest());
        String state = authorize(token, "tiktok");
        mvc.perform(get("/api/v1/social-accounts/instagram/callback").param("state", state).param("code", "approved"))
                .andExpect(status().isBadRequest());
        jdbc.update("update identity_oauth_state set expires_at=? where state_hash=?", Timestamp.from(Instant.now().minusSeconds(1)), SecureTokens.digest(state));
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state", state).param("code", "approved"))
                .andExpect(status().isBadRequest());
        verify(social, never()).exchangeCode(any(), any());
    }
    @Test void missingProviderConfigurationIsExplicitAndDoesNotPretendToLink() throws Exception {
        when(social.authorizationUri(any(), any())).thenThrow(new IdentityFailure(IdentityFailure.Code.PROVIDER_NOT_CONFIGURED));
        String token = token(register("creators"));
        mvc.perform(post("/api/v1/social-accounts/tiktok/authorizations").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PROVIDER_NOT_CONFIGURED"));
    }
    @Test void profileSavePreservesPreviouslyLinkedSocialAccounts() throws Exception {
        String token = token(register("creators"));
        String state = authorize(token, "tiktok");
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state", state).param("code", "approved")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/profiles/me/creator").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Actualizado\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/social-accounts/me").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void androidAuthorizationReturnsOnlyAttemptIdAndOwnerCanReadSuccess() throws Exception {
        String owner = token(register("creators")); String other = token(register("creators"));
        var pending = startAndroidAuthorization(owner);
        String id = pending.path("authorizationId").asText();
        String state = URI.create(pending.path("authorizationUrl").asText()).getQuery().substring("state=".length());
        mvc.perform(get("/api/v1/social-accounts/authorizations/"+id).header("Authorization","Bearer "+owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/v1/social-accounts/authorizations/"+id).header("Authorization","Bearer "+other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/social-accounts/authorizations/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state",state).param("code","approved"))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location","collabpro://social-authorization-completed?authorizationId="+id));
        mvc.perform(get("/api/v1/social-accounts/authorizations/"+id).header("Authorization","Bearer "+owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.errorCode").isEmpty());
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state",state).param("code","approved"))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/api/v1/social-accounts/authorizations/"+id).header("Authorization","Bearer "+owner))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }
    @Test void androidDenialAndDuplicateArePersistedWithoutReturningProviderSecrets() throws Exception {
        String owner = token(register("creators"));
        var denied=startAndroidAuthorization(owner); String deniedId=denied.path("authorizationId").asText();
        String deniedState=URI.create(denied.path("authorizationUrl").asText()).getQuery().substring("state=".length());
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state",deniedState).param("error","untrusted-provider-description"))
                .andExpect(status().isSeeOther());
        mvc.perform(get("/api/v1/social-accounts/authorizations/"+deniedId).header("Authorization","Bearer "+owner))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_DENIED"));
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state",authorize(owner,"tiktok")).param("code","approved"))
                .andExpect(status().isOk());
        var duplicate=startAndroidAuthorization(owner); String id=duplicate.path("authorizationId").asText();
        String state=URI.create(duplicate.path("authorizationUrl").asText()).getQuery().substring("state=".length());
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state",state).param("code","approved"))
                .andExpect(status().isSeeOther());
        mvc.perform(get("/api/v1/social-accounts/authorizations/"+id).header("Authorization","Bearer "+owner))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.errorCode").value("SOCIAL_ACCOUNT_ALREADY_LINKED"));
        mvc.perform(get("/api/v1/social-accounts/me").header("Authorization","Bearer "+owner)).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void androidProviderFailureAndExpiredAuthorizationAreQueryable() throws Exception {
        String owner = token(register("creators"));
        var failed=startAndroidAuthorization(owner); String id=failed.path("authorizationId").asText();
        String state=URI.create(failed.path("authorizationUrl").asText()).getQuery().substring("state=".length());
        when(social.exchangeCode(any(),eq("failed"))).thenThrow(new IllegalStateException("sensitive-provider-payload"));
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state",state).param("code","failed"))
                .andExpect(status().isSeeOther());
        mvc.perform(get("/api/v1/social-accounts/authorizations/"+id).header("Authorization","Bearer "+owner))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.errorCode").value("PROVIDER_FAILED"));
        var expired=startAndroidAuthorization(owner); String expiredId=expired.path("authorizationId").asText();
        String expiredState=URI.create(expired.path("authorizationUrl").asText()).getQuery().substring("state=".length());
        var past=Timestamp.from(Instant.now().minusSeconds(10));
        jdbc.update("update identity_oauth_authorization set expires_at=? where authorization_id=?",past,expiredId);
        jdbc.update("update identity_oauth_state set expires_at=? where authorization_id=?",past,expiredId);
        mvc.perform(get("/api/v1/social-accounts/authorizations/"+expiredId).header("Authorization","Bearer "+owner))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/v1/social-accounts/tiktok/callback").param("state",expiredState).param("code","approved"))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/api/v1/social-accounts/tiktok/authorizations").param("client","https://attacker.example")
                .header("Authorization","Bearer "+owner)).andExpect(status().isBadRequest());
    }
    private tools.jackson.databind.JsonNode startAndroidAuthorization(String token) throws Exception {
        var result=mvc.perform(post("/api/v1/social-accounts/tiktok/authorizations").param("client","ANDROID")
                .header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    @Test void loginAndProfileAccessWorkOverRealHttpWithSecurityFilters() throws Exception {
        String email = register("creators");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/sessions"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String token = json.readTree(response.body()).path("accessToken").asText();
        var profile = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/profiles/me/creator"))
                .header("Authorization", "Bearer " + token).GET().build();
        assertEquals(200, client.send(profile, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    private String register(String route) throws Exception {
        String email = "lifecycle-" + UUID.randomUUID() + "@example.com";
        String name = route.equals("brands") ? "businessName" : "displayName";
        mvc.perform(post("/api/v1/auth/" + route).contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + name + "\":\"Usuario\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        return email;
    }
    private String login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private String failedLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
    }
    private String token(String email) throws Exception { return json.readTree(login(email, PASSWORD)).path("accessToken").asText(); }
    private String recovery(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/recovery-requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }
    private String authorize(String token, String platform) throws Exception {
        String body = mvc.perform(post("/api/v1/social-accounts/" + platform + "/authorizations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return URI.create(json.readTree(body).path("authorizationUrl").asText()).getQuery().substring("state=".length());
    }
}
