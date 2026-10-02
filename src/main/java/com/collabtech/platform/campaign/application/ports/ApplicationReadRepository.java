package com.collabtech.platform.campaign.application.ports;

import com.collabtech.platform.campaign.application.queries.GetCreatorApplicationsQuery;
import com.collabtech.platform.campaign.application.queries.GetApplicationDetailsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.pagination.PageResult;
import java.util.Optional;

public interface ApplicationReadRepository {
    PageResult<CampaignViews.ApplicationView> findByCreator(GetCreatorApplicationsQuery query);
    Optional<CampaignViews.ApplicationView> findDetails(GetApplicationDetailsQuery query);
}
