package com.collabtech.platform.identity.infrastructure.security;

import com.collabtech.platform.shared.application.security.CurrentActor;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextActor implements CurrentActor {
    public UUID accountId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID id)) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return id;
    }
    public Set<String> roles() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", "")).collect(Collectors.toUnmodifiableSet());
    }
}
