package com.collabtech.platform.campaign.application.ports;

import com.collabtech.platform.campaign.domain.model.valueobjects.BrandId;
import java.util.UUID;

/** ACL for the authenticated account, deliberately separate from future eligibility facts. */
public interface CampaignActorGateway {
    Brand getActiveBrand(UUID accountId);
    void requireActiveCreator(UUID accountId);
    Creator getActiveCreator(UUID accountId);
    boolean isBrand(UUID accountId);
    record Brand(BrandId id, String name, String location) {}
    record Creator(com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId id,
            com.collabtech.platform.campaign.domain.services.ApplicationEligibilityService.EligibilityFacts facts) {}
}
