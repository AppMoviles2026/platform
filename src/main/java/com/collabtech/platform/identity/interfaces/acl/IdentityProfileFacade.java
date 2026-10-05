package com.collabtech.platform.identity.interfaces.acl;

import java.util.Optional;
import java.util.UUID;

/** Published Identity contract. Consumers do not receive aggregates, repositories or credentials. */
public interface IdentityProfileFacade {
    Optional<Profile> byAccount(UUID accountId);
    record Profile(UUID profileId, String name, String location, String type, boolean active, String niche, String audienceDescription, java.util.Set<String> authorizedPlatforms) {
        public Profile { authorizedPlatforms = java.util.Set.copyOf(authorizedPlatforms); }
    }
}
