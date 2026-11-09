package com.collabtech.platform.identity.infrastructure.oauth;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

final class SocialScopes {
    private SocialScopes() {}
    /** Also accepts the JSON-array representation written for Instagram by the original adapter. */
    static Set<String> parse(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.replace("[", "").replace("]", "").replace("\"", "").split("[,\\s]+"))
                .filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
    }
    static String normalized(Set<String> scopes) { return scopes.stream().sorted().collect(Collectors.joining(",")); }
}
