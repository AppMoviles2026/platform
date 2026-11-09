package com.collabtech.platform.identity.interfaces.acl;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Internal integration contract, never a REST resource. Callers authorize their own business resource first. */
public interface SocialAuthorizationFacade {
    Status status(UUID creatorProfileId, UUID socialId, Set<String> requiredScopes);
    /** Only infrastructure adapters may consume the short-lived token access; never persist or log it. */
    <T> T withAuthorizedAccess(UUID creatorProfileId, UUID socialId, Set<String> requiredScopes, AuthorizedOperation<T> operation);
    record Status(UUID socialId, String platform, String status, Set<String> grantedScopes, Set<String> missingScopes,
                  Instant expiresAt, boolean renewable, boolean reauthorizationRequired) {
        public Status { grantedScopes = Set.copyOf(grantedScopes); missingScopes = Set.copyOf(missingScopes); }
    }
    @FunctionalInterface interface AuthorizedOperation<T> { T execute(TokenAccess access); }
    final class TokenAccess {
        private final String accessToken;
        private final String platform;
        public TokenAccess(String accessToken, String platform) { this.accessToken = accessToken; this.platform = platform; }
        public String accessToken() { return accessToken; }
        public String platform() { return platform; }
        @Override public String toString() { return "TokenAccess[redacted]"; }
    }
}
