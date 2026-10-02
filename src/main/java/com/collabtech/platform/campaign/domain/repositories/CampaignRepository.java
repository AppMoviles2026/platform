package com.collabtech.platform.campaign.domain.repositories;

import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import java.util.Optional;

/** Write model persistence. Search is a separate application read port. */
public interface CampaignRepository {
    Campaign save(Campaign campaign);
    Optional<Campaign> findById(CampaignId id);
}
