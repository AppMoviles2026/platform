package com.collabtech.platform.campaign.application.services;

import com.collabtech.platform.campaign.application.queries.SearchCampaignsQuery;
import com.collabtech.platform.campaign.application.queries.GetCampaignDetailsQuery;
import com.collabtech.platform.campaign.application.queries.GetBrandCampaignsQuery;
import com.collabtech.platform.campaign.application.queries.GetCreatorApplicationsQuery;
import com.collabtech.platform.campaign.application.queries.GetApplicationDetailsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.pagination.PageResult;

/** Inbound read port only; no persistence or endpoint implementation yet. */
public interface CampaignQueryService {
    PageResult<CampaignViews.Summary> handle(SearchCampaignsQuery query);
    CampaignViews.Details handle(GetCampaignDetailsQuery query);
    PageResult<CampaignViews.Summary> handle(GetBrandCampaignsQuery query);
    PageResult<CampaignViews.ApplicationView> handle(GetCreatorApplicationsQuery query);
    CampaignViews.ApplicationView handle(GetApplicationDetailsQuery query);
}
