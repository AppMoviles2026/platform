package com.collabtech.platform.campaign.application.queries;

import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.cqrs.Query;

public record GetCampaignDetailsQuery(CampaignId campaignId) implements Query<CampaignViews.Details> {}
