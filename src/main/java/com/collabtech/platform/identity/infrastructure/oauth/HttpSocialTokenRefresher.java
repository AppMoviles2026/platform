package com.collabtech.platform.identity.infrastructure.oauth;

import com.collabtech.platform.identity.application.exceptions.IdentityFailure;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;

/** Token protocols stay in Infrastructure. Instagram short-lived credentials require authorization again. */
public final class HttpSocialTokenRefresher {
    private final Map<String, HttpSocialOAuthClient.Provider> providers;
    private final RestClient http;
    private final Clock clock;
    public HttpSocialTokenRefresher(Map<String, HttpSocialOAuthClient.Provider> providers, RestClient http, Clock clock) {
        this.providers = Map.copyOf(providers); this.http = http; this.clock = clock;
    }
    public boolean configured(String platform) {
        var p = providers.get(platform);
        return p != null && !p.clientId().isBlank() && !p.clientSecret().isBlank();
    }
    public boolean supports(String platform) { return "tiktok".equals(platform); }
    public Refreshed refresh(String platform, String refreshToken, String externalId) {
        if (!configured(platform)) throw new IdentityFailure(IdentityFailure.Code.PROVIDER_NOT_CONFIGURED);
        if (!supports(platform)) throw new IdentityFailure(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED);
        try {
            var p = providers.get(platform);
            var form = new LinkedMultiValueMap<String, String>();
            form.add("client_key", p.clientId()); form.add("client_secret", p.clientSecret());
            form.add("grant_type", "refresh_token"); form.add("refresh_token", refreshToken);
            var token = http.post().uri(p.tokenUri()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
            if (token == null) throw new IdentityFailure(IdentityFailure.Code.PROVIDER_FAILED);
            String error = token.path("error").asText("");
            if (!error.isBlank()) {
                if (Set.of("invalid_grant", "invalid_token", "access_denied").contains(error))
                    throw new IdentityFailure(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED);
                throw new IdentityFailure(IdentityFailure.Code.PROVIDER_FAILED);
            }
            String access = token.path("access_token").asText("");
            String refresh = token.path("refresh_token").asText("");
            long seconds = token.path("expires_in").asLong(0);
            long refreshSeconds = token.path("refresh_expires_in").asLong(0);
            var scopes = SocialScopes.parse(token.path("scope").asText(""));
            if (access.isBlank() || refresh.isBlank() || seconds <= 0 || refreshSeconds <= 0
                    || !externalId.equals(token.path("open_id").asText("")) || !scopes.contains("user.info.basic"))
                throw new IdentityFailure(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED);
            return new Refreshed(access, refresh, scopes, clock.instant().plusSeconds(seconds), clock.instant().plusSeconds(refreshSeconds));
        } catch (IdentityFailure failure) { throw failure; }
        catch (HttpClientErrorException failure) {
            try {
                String error = tools.jackson.databind.json.JsonMapper.builder().build().readTree(failure.getResponseBodyAsString()).path("error").asText("");
                if (Set.of("invalid_grant", "invalid_token", "access_denied").contains(error))
                    throw new IdentityFailure(IdentityFailure.Code.SOCIAL_REAUTHORIZATION_REQUIRED);
            } catch (IdentityFailure identityFailure) { throw identityFailure; }
            catch (RuntimeException invalidResponse) { /* A malformed/configuration error is not user revocation. */ }
            throw new IdentityFailure(IdentityFailure.Code.PROVIDER_FAILED);
        } catch (org.springframework.web.client.RestClientException | IllegalArgumentException | java.time.DateTimeException failure) {
            throw new IdentityFailure(IdentityFailure.Code.PROVIDER_FAILED);
        }
    }
    public record Refreshed(String accessToken, String refreshToken, Set<String> scopes, Instant expiresAt, Instant refreshExpiresAt) {
        public Refreshed { scopes = Set.copyOf(scopes); }
        @Override public String toString() { return "Refreshed[redacted]"; }
    }
}
