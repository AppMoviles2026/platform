package com.collabtech.platform.campaign.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record ApplicationId(UUID value) {
    public ApplicationId { Objects.requireNonNull(value, "value"); }
}
