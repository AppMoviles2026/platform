package com.collabtech.platform.campaign.domain.model.valueobjects;

/** Shared text invariant inside Campaign, not a cross-context business model. */
public final class CampaignText {
    private CampaignText() {}
    public static String required(String value, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max) throw new IllegalArgumentException("Invalid campaign text");
        return value.strip();
    }
    public static String optional(String value, int max) { return value == null || value.isBlank() ? null : required(value, max); }
}
