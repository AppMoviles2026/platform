package com.collabtech.platform.campaign.application.ports;

import java.util.UUID;
import java.util.function.Supplier;

/** Atomically persists a command result with its changes, scoped to the acting account. */
public interface IdempotentCommands {
    <T> T execute(UUID actor, String operation, String key, String fingerprint, Class<T> resultType, Supplier<T> work);
}
