package com.collabtech.platform.campaign.domain.services;

import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.valueobjects.RequirementId;
import java.util.List;
import java.util.Set;

/** Pure business-policy port; implementation must not query OAuth or a database. */
public interface ApplicationEligibilityService {
    EligibilityResult evaluate(Campaign campaign, EligibilityFacts creatorFacts);

    record EligibilityFacts(String niche, String audienceDescription, Set<String> authorizedPlatforms) {
        public EligibilityFacts { authorizedPlatforms = Set.copyOf(authorizedPlatforms); }
    }

    record EligibilityResult(List<RequirementId> unmetRequirements) {
        public EligibilityResult { unmetRequirements = List.copyOf(unmetRequirements); }
        public boolean eligible() { return unmetRequirements.isEmpty(); }
    }
}
