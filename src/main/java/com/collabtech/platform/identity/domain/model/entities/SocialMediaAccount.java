package com.collabtech.platform.identity.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialMediaAccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialAccountStatus;

/** Structural model only. Business transitions are scheduled in docs/implementation-plan.md. */
public final class SocialMediaAccount extends Entity<SocialMediaAccountId> {
    private final SocialPlatform platform;
    private final String externalAccountId;
    private final String username;
    private final SocialAccountStatus status;

    public SocialMediaAccount(SocialMediaAccountId id, SocialPlatform platform, String externalAccountId, String username, SocialAccountStatus status) {
        super(id);
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
