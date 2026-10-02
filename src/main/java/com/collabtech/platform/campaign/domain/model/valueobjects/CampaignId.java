package com.collabtech.platform.campaign.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record CampaignId(UUID value) {
    public CampaignId { Objects.requireNonNull(value, "value"); }
}
