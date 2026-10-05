package com.collabtech.platform.campaign.domain.services;

import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.valueobjects.RequirementId;
import java.util.List;
import java.util.Set;

/** Pure business-policy port; implementation must not query OAuth or a database. */
public interface ApplicationEligibilityService {
    EligibilityResult evaluate(Campaign campaign, EligibilityFacts creatorFacts);

    record EligibilityFacts(String niche, String location, String audienceDescription, Set<String> authorizedPlatforms, Set<RequirementId> confirmations) {
        public EligibilityFacts { authorizedPlatforms = Set.copyOf(authorizedPlatforms); confirmations = Set.copyOf(confirmations); }
        public EligibilityFacts(String niche, String audienceDescription, Set<String> platforms) { this(niche, null, audienceDescription, platforms, Set.of()); }
    }

    record EligibilityResult(List<RequirementId> unmetRequirements) {
        public EligibilityResult { unmetRequirements = List.copyOf(unmetRequirements); }
        public boolean eligible() { return unmetRequirements.isEmpty(); }
    }
}
