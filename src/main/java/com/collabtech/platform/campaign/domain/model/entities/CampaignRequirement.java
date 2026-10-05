package com.collabtech.platform.campaign.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.campaign.domain.model.valueobjects.RequirementId;

/** A requirement owned by a campaign, not an independently saved aggregate. */
public final class CampaignRequirement extends Entity<RequirementId> {
    private final String description;
    private final boolean mandatory;
    private final com.collabtech.platform.campaign.domain.model.valueobjects.RequirementRule rule;

    public CampaignRequirement(RequirementId id, String description, boolean mandatory) {
        this(id, description, mandatory, com.collabtech.platform.campaign.domain.model.valueobjects.RequirementRule.manual());
    }
    public CampaignRequirement(RequirementId id, String description, boolean mandatory,
            com.collabtech.platform.campaign.domain.model.valueobjects.RequirementRule rule) {
        super(id);
        this.description = com.collabtech.platform.campaign.domain.model.valueobjects.CampaignText.required(description, 2000);
        this.mandatory = mandatory;
        this.rule = java.util.Objects.requireNonNull(rule);
    }

    public String description() { return description; }
    public boolean mandatory() { return mandatory; }
    public com.collabtech.platform.campaign.domain.model.valueobjects.RequirementRule rule() { return rule; }
}
