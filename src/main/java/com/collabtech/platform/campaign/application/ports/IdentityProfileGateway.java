package com.collabtech.platform.campaign.application.ports;

import com.collabtech.platform.campaign.domain.model.valueobjects.BrandId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CreatorId;
import com.collabtech.platform.campaign.domain.services.ApplicationEligibilityService;

/** Campaign's ACL contract. No imports of Identity aggregates or persistence tables. */
public interface IdentityProfileGateway {
    BrandSnapshot getActiveBrand(BrandId id);
    CreatorSnapshot getActiveCreator(CreatorId id);
    record BrandSnapshot(BrandId id, String name, String location) {}
    record CreatorSnapshot(CreatorId id, ApplicationEligibilityService.EligibilityFacts eligibility) {}
}
