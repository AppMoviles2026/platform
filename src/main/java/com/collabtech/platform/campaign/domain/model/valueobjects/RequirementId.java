package com.collabtech.platform.campaign.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record RequirementId(UUID value) {
    public RequirementId { Objects.requireNonNull(value, "value"); }
}
