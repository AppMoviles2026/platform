package com.collabtech.platform.shared.application.security;

import java.util.Set;
import java.util.UUID;

/** Implement from authenticated server credentials, never from a client-selected role. */
public interface CurrentActor {
    UUID accountId();
    Set<String> roles();
}
