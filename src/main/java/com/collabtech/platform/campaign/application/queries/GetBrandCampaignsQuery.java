package com.collabtech.platform.campaign.application.queries;

import com.collabtech.platform.campaign.domain.model.valueobjects.BrandId;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.cqrs.Query;
import com.collabtech.platform.shared.application.pagination.PageRequest;
import com.collabtech.platform.shared.application.pagination.PageResult;

public record GetBrandCampaignsQuery(BrandId brandId, PageRequest page) implements Query<PageResult<CampaignViews.Summary>> {}
