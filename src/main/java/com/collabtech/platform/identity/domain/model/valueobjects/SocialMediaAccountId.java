package com.collabtech.platform.identity.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record SocialMediaAccountId(UUID value) {
    public SocialMediaAccountId { Objects.requireNonNull(value, "value"); }
}
