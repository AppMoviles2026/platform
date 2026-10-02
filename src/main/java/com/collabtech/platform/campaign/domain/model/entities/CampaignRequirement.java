package com.collabtech.platform.campaign.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.campaign.domain.model.valueobjects.RequirementId;

/** Structural model only. Business transitions are scheduled in docs/implementation-plan.md. */
public final class CampaignRequirement extends Entity<RequirementId> {
    private final String description;
    private final boolean mandatory;

    public CampaignRequirement(RequirementId id, String description, boolean mandatory) {
        super(id);
        this.description = description;
        this.mandatory = mandatory;
    }

    public String description() { return description; }
    public boolean mandatory() { return mandatory; }
}
