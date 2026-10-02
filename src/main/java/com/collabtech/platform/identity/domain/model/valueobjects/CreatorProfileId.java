package com.collabtech.platform.identity.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record CreatorProfileId(UUID value) {
    public CreatorProfileId { Objects.requireNonNull(value, "value"); }
}
