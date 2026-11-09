package com.collabtech.platform.identity.application.projections;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** Read projections. Never expose password hashes or provider tokens. */
public final class IdentityViews {
    private IdentityViews() {}
    public record AccountView(UUID accountId, UUID profileId, String name, String accountType, String status) {}
    public record BrandProfileView(UUID profileId, String businessName, String description, String category,
                                   String location, long version) {}
    public record CreatorProfileView(UUID profileId, String displayName, String biography, String niche,
                                     String audienceDescription, String location) {}
    public record SocialAccountView(UUID id, String platform, String username, String status) {}
    public record SocialAccessView(UUID socialId, String platform, String status, java.util.Set<String> grantedScopes,
            java.util.Set<String> missingScopes, Instant expiresAt, boolean renewable, boolean reauthorizationRequired) {
        public SocialAccessView {
            grantedScopes = java.util.Set.copyOf(grantedScopes); missingScopes = java.util.Set.copyOf(missingScopes);
        }
    }
    public record SessionView(AccountView account, String accessToken, String tokenType, Instant expiresAt) {
        @Override public String toString() { return "SessionView[accessToken=<redacted>]"; }
    }
    public record AuthorizationView(URI authorizationUrl, UUID authorizationId) {}
    public record AuthorizationStatusView(UUID authorizationId, String platform, String status, String errorCode, Instant expiresAt) {}
}
