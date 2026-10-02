package com.collabtech.platform.campaign.application.ports;

import com.collabtech.platform.campaign.application.queries.SearchCampaignsQuery;
import com.collabtech.platform.campaign.application.queries.GetCampaignDetailsQuery;
import com.collabtech.platform.campaign.application.queries.GetBrandCampaignsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.pagination.PageResult;
import java.util.Optional;

public interface CampaignReadRepository {
    PageResult<CampaignViews.Summary> search(SearchCampaignsQuery query);
    PageResult<CampaignViews.Summary> findByBrand(GetBrandCampaignsQuery query);
    Optional<CampaignViews.Details> findDetails(GetCampaignDetailsQuery query);
}
