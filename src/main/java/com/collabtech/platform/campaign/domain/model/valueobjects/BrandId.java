package com.collabtech.platform.campaign.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/** External reference to Identity BrandProfile, not a JPA relationship. */
public record BrandId(UUID value) {
    public BrandId { Objects.requireNonNull(value, "value"); }
}
