package com.collabtech.platform.identity.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record BrandProfileId(UUID value) {
    public BrandProfileId { Objects.requireNonNull(value, "value"); }
}
