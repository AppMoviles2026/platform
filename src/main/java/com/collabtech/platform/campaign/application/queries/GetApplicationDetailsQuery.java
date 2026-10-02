package com.collabtech.platform.campaign.application.queries;

import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.cqrs.Query;

public record GetApplicationDetailsQuery(CreatorId creatorId, ApplicationId applicationId) implements Query<CampaignViews.ApplicationView> {}
