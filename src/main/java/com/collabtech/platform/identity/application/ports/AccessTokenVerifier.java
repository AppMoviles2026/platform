package com.collabtech.platform.identity.application.ports;

import java.util.Optional;
import java.util.UUID;

/** Verified identity only; cryptographic and persistence details remain in infrastructure. */
public interface AccessTokenVerifier {
    Optional<AuthenticatedAccount> authenticate(String token);
    record AuthenticatedAccount(UUID accountId, String role) {}
}
