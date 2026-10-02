package com.collabtech.platform.campaign.domain.model.aggregates;

import com.collabtech.platform.shared.domain.model.AggregateRoot;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationStatus;

import java.time.Instant;

/** Structural model only. Business transitions are scheduled in docs/implementation-plan.md. */
public final class Application extends AggregateRoot<ApplicationId> {
    private final CampaignId campaignId;
    private final CreatorId creatorId;
    private final String message;
    private final ApplicationStatus status;
    private final Instant submittedAt;

    public Application(ApplicationId id, CampaignId campaignId, CreatorId creatorId, String message, ApplicationStatus status, Instant submittedAt) {
        super(id);
        this.campaignId = campaignId;
        this.creatorId = creatorId;
        this.message = message;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    public CampaignId campaignId() { return campaignId; }
    public CreatorId creatorId() { return creatorId; }
    public String message() { return message; }
    public ApplicationStatus status() { return status; }
    public Instant submittedAt() { return submittedAt; }
}
