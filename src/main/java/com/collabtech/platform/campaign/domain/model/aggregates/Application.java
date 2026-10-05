package com.collabtech.platform.campaign.domain.model.aggregates;

import com.collabtech.platform.shared.domain.model.AggregateRoot;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import com.collabtech.platform.campaign.domain.model.valueobjects.RequirementId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignText;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.campaign.domain.events.ApplicationSubmitted;

/** Independent aggregate: a creator can edit/cancel only while the application is pending. */
public final class Application extends AggregateRoot<ApplicationId> {
    private final CampaignId campaignId;
    private final CreatorId creatorId;
    private String message;
    private ApplicationStatus status;
    private final Instant submittedAt;
    private final Set<RequirementId> confirmations;
    private long version;

    public Application(ApplicationId id, CampaignId campaignId, CreatorId creatorId, String message, ApplicationStatus status, Instant submittedAt) {
        this(id, campaignId, creatorId, message, status, submittedAt, Set.of());
    }
    public Application(ApplicationId id, CampaignId campaignId, CreatorId creatorId, String message, ApplicationStatus status, Instant submittedAt, Set<RequirementId> confirmations) {
        super(id);
        this.campaignId = Objects.requireNonNull(campaignId);
        this.creatorId = Objects.requireNonNull(creatorId);
        this.message = CampaignText.required(message, 4000);
        this.status = Objects.requireNonNull(status);
        this.submittedAt = Objects.requireNonNull(submittedAt);
        this.confirmations = Set.copyOf(confirmations);
    }

    public CampaignId campaignId() { return campaignId; }
    public CreatorId creatorId() { return creatorId; }
    public String message() { return message; }
    public ApplicationStatus status() { return status; }
    public Instant submittedAt() { return submittedAt; }
    public Set<RequirementId> confirmations() { return confirmations; }
    public long version() { return version; }
    public void restoreVersion(long version) { if (version < 0) throw new IllegalArgumentException("Invalid version"); this.version = version; }
    public static Application submit(Campaign campaign, CreatorId creator, String message, Set<RequirementId> confirmations, Instant now) {
        if (!campaign.acceptsApplications(now)) throw new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_ACCEPTING_APPLICATIONS);
        var result = new Application(new ApplicationId(UUID.randomUUID()), campaign.id(), creator, message, ApplicationStatus.PENDING, now, confirmations);
        result.recordEvent(new ApplicationSubmitted(UUID.randomUUID(), now, result.id()));
        return result;
    }
    public void updateMessage(String message) { requirePending(); this.message = CampaignText.required(message, 4000); }
    public void cancel() { requirePending(); this.status = ApplicationStatus.CANCELLED; }
    private void requirePending() { if (status != ApplicationStatus.PENDING) throw new CampaignFailure(CampaignFailure.Code.APPLICATION_NOT_PENDING); }
}
