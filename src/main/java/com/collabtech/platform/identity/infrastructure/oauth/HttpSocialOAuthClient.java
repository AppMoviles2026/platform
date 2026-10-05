package com.collabtech.platform.identity.infrastructure.oauth;

import com.collabtech.platform.identity.application.ports.SocialOAuthClient;
import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialMediaAccountId;
import com.collabtech.platform.identity.infrastructure.security.SecretCipher;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Anti-corruption adapter: only verified external identity and a credential receipt leave Infrastructure. */
public final class HttpSocialOAuthClient implements SocialOAuthClient {
    public record Provider(String clientId, String clientSecret, String redirectUri,
            String authorizationUri, String tokenUri, String userInfoUri, String scopes) {
        @Override public String toString() { return "Provider[credentials redacted]"; }
    }
    private final Map<String, Provider> providers;
    private final RestClient http;
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final Clock clock;

    public HttpSocialOAuthClient(Map<String, Provider> providers, RestClient http, JdbcTemplate jdbc, SecretCipher cipher, Clock clock) {
        this.providers = Map.copyOf(providers); this.http = http; this.jdbc = jdbc; this.cipher = cipher; this.clock = clock;
    }
    public URI authorizationUri(SocialPlatform platform, String state) {
        var provider = configured(platform);
        String clientParameter = platform.code().equals("tiktok") ? "client_key" : "client_id";
        return URI.create(provider.authorizationUri() + "?" + clientParameter + "=" + encode(provider.clientId())
                + "&response_type=code&redirect_uri=" + encode(provider.redirectUri()) + "&scope=" + encode(provider.scopes())
                + "&state=" + encode(state));
    }
    public AuthorizedAccount exchangeCode(SocialPlatform platform, String authorizationCode) {
        var provider = configured(platform);
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add(platform.code().equals("tiktok") ? "client_key" : "client_id", provider.clientId());
            form.add("client_secret", provider.clientSecret()); form.add("redirect_uri", provider.redirectUri());
            form.add("grant_type", "authorization_code"); form.add("code", authorizationCode);
            var token = http.post().uri(provider.tokenUri())
                    .contentType(platform.code().equals("instagram") ? MediaType.MULTIPART_FORM_DATA : MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
            if (token == null) denied();
            if (token.has("error") && !token.path("error").isNull()) denied();
            // Some Instagram deployments wrap the token payload in data[].
            if (token.path("data").isArray()) token = token.path("data").path(0);
            String access = token.path("access_token").asText("");
            if (access.isBlank()) denied();
            String granted = token.path("scope").asText("");
            if (platform.code().equals("tiktok") && !containsScope(granted, "user.info.basic")) denied();
            if (platform.code().equals("instagram") && token.path("permissions").isArray()) {
                boolean basic = false;
                for (var permission : token.path("permissions")) {
                    if (permission.asText().equals("instagram_business_basic")) basic = true;
                }
                if (!basic) denied();
                granted = token.path("permissions").toString();
            }
            var info = http.get().uri(provider.userInfoUri()).header("Authorization", "Bearer " + access)
                    .retrieve().body(JsonNode.class);
            if (info == null) denied();
            JsonNode user = platform.code().equals("tiktok") ? info.path("data").path("user") : info;
            if (platform.code().equals("tiktok") && !info.path("error").path("code").asText("ok").equals("ok")) denied();
            String externalId = platform.code().equals("tiktok") ? user.path("open_id").asText("")
                    : user.path("user_id").asText(user.path("id").asText(""));
            String name = platform.code().equals("tiktok") ? user.path("display_name").asText("")
                    : user.path("username").asText("");
            if (externalId.isBlank() || name.isBlank()) denied();
            String tokenIdentity = token.path(platform.code().equals("tiktok") ? "open_id" : "user_id").asText("");
            if (!tokenIdentity.isBlank() && !tokenIdentity.equals(externalId)) denied();
            UUID receipt = UUID.randomUUID();
            String refresh = token.path("refresh_token").asText("");
            long seconds = token.path("expires_in").asLong(3600);
            if (seconds <= 0) denied();
            jdbc.update("insert into identity_provider_credentials(credential_id,encrypted_access_token,encrypted_refresh_token,scopes,expires_at) values(?,?,?,?,?)",
                    receipt.toString(), cipher.encrypt(access), refresh.isBlank() ? null : cipher.encrypt(refresh),
                    granted.isBlank() ? provider.scopes() : granted, Timestamp.from(clock.instant().plusSeconds(seconds)));
            return new AuthorizedAccount(externalId, name, receipt);
        } catch (IdentityFailure failure) { throw failure; }
        catch (org.springframework.web.client.HttpClientErrorException failure) { denied(); return null; }
        catch (org.springframework.web.client.RestClientException | IllegalArgumentException failure) {
            throw new IdentityFailure(IdentityFailure.Code.PROVIDER_FAILED);
        }
    }
    public void attachCredentials(UUID receipt, SocialMediaAccountId socialId) {
        if (jdbc.update("update identity_provider_credentials set social_id=? where credential_id=? and social_id is null",
                socialId.value().toString(), receipt.toString()) != 1) throw new IdentityFailure(IdentityFailure.Code.PROVIDER_FAILED);
    }
    private Provider configured(SocialPlatform platform) {
        var provider = providers.get(platform.code());
        if (provider == null || provider.clientId().isBlank() || provider.clientSecret().isBlank() || provider.redirectUri().isBlank()) {
            throw new IdentityFailure(IdentityFailure.Code.PROVIDER_NOT_CONFIGURED);
        }
        URI redirect;
        try { redirect = URI.create(provider.redirectUri()); }
        catch (IllegalArgumentException failure) { throw new IdentityFailure(IdentityFailure.Code.PROVIDER_NOT_CONFIGURED); }
        if (!"https".equals(redirect.getScheme()) || redirect.getHost() == null || redirect.getFragment() != null) {
            throw new IdentityFailure(IdentityFailure.Code.PROVIDER_NOT_CONFIGURED);
        }
        return provider;
    }
    private static boolean containsScope(String scopes, String scope) {
        return java.util.Arrays.asList(scopes.split("[,\\s]+")).contains(scope);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void denied() { throw new IdentityFailure(IdentityFailure.Code.AUTHORIZATION_DENIED); }
}
