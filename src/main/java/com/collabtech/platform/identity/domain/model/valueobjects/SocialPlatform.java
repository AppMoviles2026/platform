package com.collabtech.platform.identity.domain.model.valueobjects;

import java.util.Locale;
import java.util.Objects;

public record SocialPlatform(String code) {
    public SocialPlatform {
        Objects.requireNonNull(code, "code");
        code = code.strip().toLowerCase(Locale.ROOT);
        if (code.isEmpty()) throw new IllegalArgumentException("Platform code required");
    }
}
