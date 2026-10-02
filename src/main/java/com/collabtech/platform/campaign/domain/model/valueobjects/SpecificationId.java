package com.collabtech.platform.campaign.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record SpecificationId(UUID value) {
    public SpecificationId { Objects.requireNonNull(value, "value"); }
}
