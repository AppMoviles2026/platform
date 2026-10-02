package com.collabtech.platform.campaign.domain.events;

import com.collabtech.platform.shared.domain.events.DomainEvent;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import java.time.Instant;
import java.util.UUID;

/** Event contract reserved for future aggregate transitions. */
public record ApplicationSubmitted(UUID eventId, Instant occurredAt, ApplicationId applicationId) implements DomainEvent {}
