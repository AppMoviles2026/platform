package com.collabtech.platform.campaign.domain.repositories;

import com.collabtech.platform.campaign.domain.model.aggregates.Application;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId;
import java.util.Optional;

public interface ApplicationRepository {
    Application save(Application application);
    Optional<Application> findById(ApplicationId id);
    boolean existsByCampaignAndCreator(CampaignId campaignId, CreatorId creatorId);
}
