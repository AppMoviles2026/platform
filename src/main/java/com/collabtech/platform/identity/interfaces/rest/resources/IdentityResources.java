package com.collabtech.platform.identity.interfaces.rest.resources;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.interfaces.rest.transform.RegistrationResourceAssembler;

public final class IdentityResources {
    private IdentityResources() {}
    public record Session(AccountResource account, String accessToken, String tokenType, Instant expiresAt) {
        @Override public String toString() { return "Session[accessToken redacted]"; }
    }
    public record CreatorProfile(UUID profileId, String displayName, String biography, String niche, String audienceDescription, String location) {}
    public record SocialAccount(UUID id, String platform, String username, String status) {}
    public record Authorization(URI authorizationUrl) {}
    public record RecoveryAccepted(String message) {}
    public static Session session(IdentityViews.SessionView view) {
        return new Session(RegistrationResourceAssembler.toResource(view.account()), view.accessToken(), view.tokenType(), view.expiresAt());
    }
    public static CreatorProfile profile(IdentityViews.CreatorProfileView view) {
        return new CreatorProfile(view.profileId(), view.displayName(), view.biography(), view.niche(), view.audienceDescription(), view.location());
    }
    public static SocialAccount social(IdentityViews.SocialAccountView view) {
        return new SocialAccount(view.id(), view.platform(), view.username(), view.status());
    }
}
