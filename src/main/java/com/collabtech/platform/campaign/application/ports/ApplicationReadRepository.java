package com.collabtech.platform.campaign.application.ports;

import com.collabtech.platform.campaign.application.queries.GetCreatorApplicationsQuery;
import com.collabtech.platform.campaign.application.queries.GetApplicationDetailsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.pagination.PageResult;
import java.util.Optional;

public interface ApplicationReadRepository {
    PageResult<CampaignViews.ApplicationView> findByCreator(GetCreatorApplicationsQuery query);
    PageResult<CampaignViews.ApplicationView> byCampaign(com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId id,
            com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationStatus status, com.collabtech.platform.shared.application.pagination.PageRequest page);
    Optional<CampaignViews.ApplicationView> findDetails(GetApplicationDetailsQuery query);
}
