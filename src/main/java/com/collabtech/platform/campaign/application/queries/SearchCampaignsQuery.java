package com.collabtech.platform.campaign.application.queries;

import com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CompensationType;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.cqrs.Query;
import com.collabtech.platform.shared.application.pagination.PageRequest;
import com.collabtech.platform.shared.application.pagination.PageResult;

public record SearchCampaignsQuery(CreatorId creatorId, String text, String category, String location, CompensationType compensationType, PageRequest page) implements Query<PageResult<CampaignViews.Summary>> {
    public SearchCampaignsQuery {
        java.util.Objects.requireNonNull(creatorId); java.util.Objects.requireNonNull(page);
        text = com.collabtech.platform.campaign.domain.model.valueobjects.CampaignText.optional(text, 200);
        category = com.collabtech.platform.campaign.domain.model.valueobjects.CampaignText.optional(category, 100);
        location = com.collabtech.platform.campaign.domain.model.valueobjects.CampaignText.optional(location, 150);
    }
}
