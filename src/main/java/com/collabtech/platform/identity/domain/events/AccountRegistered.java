package com.collabtech.platform.identity.domain.events;

import com.collabtech.platform.shared.domain.events.DomainEvent;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import java.time.Instant;
import java.util.UUID;

/** Event contract reserved for future aggregate transitions. */
public record AccountRegistered(UUID eventId, Instant occurredAt, AccountId accountId) implements DomainEvent {}
