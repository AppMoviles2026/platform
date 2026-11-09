package com.collabtech.platform.identity.interfaces.acl;

import java.util.Optional;
import java.util.UUID;

/** Published Identity contract. Consumers do not receive aggregates, repositories or credentials. */
public interface IdentityProfileFacade {
    Optional<Profile> byAccount(UUID accountId);
    /** Trusted internal consumers must first authorize access to their campaign/collaboration. */
    Optional<BrandDetails> brandById(UUID brandProfileId);
    Optional<CreatorDetails> creatorById(UUID creatorProfileId);
    record BrandDetails(UUID profileId, String businessName, String description, String category, String location, boolean active) {}
    record SocialIdentity(UUID socialId, String platform, String username) {}
    record CreatorDetails(UUID profileId, String displayName, String biography, String niche, String audienceDescription,
                          String location, boolean active, java.util.List<SocialIdentity> socialAccounts) {
        public CreatorDetails { socialAccounts = java.util.List.copyOf(socialAccounts); }
    }
    record Profile(UUID profileId, String name, String location, String type, boolean active, String niche, String audienceDescription, java.util.Set<String> authorizedPlatforms) {
        public Profile { authorizedPlatforms = java.util.Set.copyOf(authorizedPlatforms); }
    }
}
