package com.collabtech.platform.campaign.domain.services;

import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;

public final class ExplicitApplicationEligibilityService implements ApplicationEligibilityService {
    public EligibilityResult evaluate(Campaign campaign, EligibilityFacts facts) {
        return new EligibilityResult(campaign.requirements().stream().filter(item -> item.mandatory()).filter(item -> {
            var rule = item.rule();
            return !switch (rule.type()) {
                case MANUAL_CONFIRMATION -> facts.confirmations().contains(item.id());
                case NICHE_EQUALS -> same(facts.niche(), rule.expectedValue());
                case LOCATION_EQUALS -> same(facts.location(), rule.expectedValue());
                case AUTHORIZED_PLATFORM -> facts.authorizedPlatforms().contains(rule.expectedValue());
            };
        }).map(item -> item.id()).toList());
    }
    private static boolean same(String actual, String expected) { return actual != null && actual.strip().equalsIgnoreCase(expected); }
}
