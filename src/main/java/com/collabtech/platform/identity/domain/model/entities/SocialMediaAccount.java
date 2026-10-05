package com.collabtech.platform.identity.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialMediaAccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialAccountStatus;

/** Verified provider identity owned by a creator profile; credentials stay outside the domain. */
public final class SocialMediaAccount extends Entity<SocialMediaAccountId> {
    private final SocialPlatform platform;
    private final String externalAccountId;
    private final String username;
    private final SocialAccountStatus status;

    public SocialMediaAccount(SocialMediaAccountId id, SocialPlatform platform, String externalAccountId, String username, SocialAccountStatus status) {
        super(id);
        java.util.Objects.requireNonNull(platform);
        java.util.Objects.requireNonNull(status);
        if (externalAccountId == null || externalAccountId.isBlank() || externalAccountId.length() > 255
                || username == null || username.isBlank() || username.length() > 255) {
            throw new IllegalArgumentException("Authorized social identity is required");
        }
        this.platform = platform;
        this.externalAccountId = externalAccountId;
        this.username = username;
        this.status = status;
    }

    public SocialPlatform platform() { return platform; }
    public String externalAccountId() { return externalAccountId; }
    public String username() { return username; }
    public SocialAccountStatus status() { return status; }
}
