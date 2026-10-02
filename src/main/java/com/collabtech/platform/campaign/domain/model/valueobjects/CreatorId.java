package com.collabtech.platform.campaign.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/** External reference to Identity CreatorProfile, not a JPA relationship. */
public record CreatorId(UUID value) {
    public CreatorId { Objects.requireNonNull(value, "value"); }
}
