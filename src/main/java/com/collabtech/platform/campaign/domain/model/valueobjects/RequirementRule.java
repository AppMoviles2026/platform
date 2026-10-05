package com.collabtech.platform.campaign.domain.model.valueobjects;

import java.util.Locale;

/** Explicit rule, never inferred by interpreting a free-text description. */
public record RequirementRule(Type type, String expectedValue) {
    public enum Type { MANUAL_CONFIRMATION, NICHE_EQUALS, LOCATION_EQUALS, AUTHORIZED_PLATFORM }
    public RequirementRule {
        if (type == null) throw new IllegalArgumentException("Requirement rule required");
        if (type == Type.MANUAL_CONFIRMATION) {
            if (expectedValue != null && !expectedValue.isBlank()) throw new IllegalArgumentException("Manual requirements have no automatic value");
            expectedValue = null;
        } else {
            expectedValue = CampaignText.required(expectedValue, 150);
            if (type == Type.AUTHORIZED_PLATFORM) {
                expectedValue = expectedValue.toLowerCase(Locale.ROOT);
                if (!java.util.Set.of("instagram", "tiktok").contains(expectedValue)) throw new IllegalArgumentException("Unsupported platform");
            }
        }
    }
    public static RequirementRule manual() { return new RequirementRule(Type.MANUAL_CONFIRMATION, null); }
}
