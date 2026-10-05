package com.collabtech.platform.campaign.application.projections;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import java.time.Instant;

public final class CampaignViewMapper {
    private CampaignViewMapper() {}
    public static CampaignViews.Summary summary(Campaign campaign, String brandName) {
        return new CampaignViews.Summary(campaign.id().value(), campaign.brandId().value(), brandName, campaign.title(), campaign.category(),
                campaign.location(), campaign.compensationTerms(), campaign.applicationDeadline(), campaign.status().name());
    }
    public static CampaignViews.Details details(Campaign campaign, String brandName, Instant now) {
        return new CampaignViews.Details(summary(campaign, brandName), campaign.objective(), campaign.description(), campaign.targetAudience(),
                campaign.requirements().stream().map(item -> new CampaignViews.Requirement(item.id().value(), item.description(), item.mandatory(), item.rule())).toList(),
                campaign.deliverables().stream().map(item -> new CampaignViews.Deliverable(item.id().value(), item.contentType(), item.description(), item.quantity(), item.deadline())).toList(),
                campaign.acceptsApplications(now), campaign.publicationDate());
    }
}
