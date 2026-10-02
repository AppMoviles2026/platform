package com.collabtech.platform.identity.application.projections;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** Read projections. Never expose password hashes or provider tokens. */
public final class IdentityViews {
    private IdentityViews() {}
    public record AccountView(UUID accountId, UUID profileId, String name, String accountType, String status) {}
    public record CreatorProfileView(UUID profileId, String displayName, String biography, String niche,
                                     String audienceDescription, String location) {}
    public record SocialAccountView(UUID id, String platform, String username, String status) {}
    public record SessionView(AccountView account, String accessToken, String tokenType, Instant expiresAt) {
        @Override public String toString() { return "SessionView[accessToken=<redacted>]"; }
    }
    public record AuthorizationView(URI authorizationUrl) {}
}
