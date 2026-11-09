package com.collabtech.platform.collaboration.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Schema v1: committed execution fact; no payment is inferred from approval or completion. */
public record DeliveryEvent(UUID eventId, UUID collaborationId, String type, UUID deliverableId,
                            UUID submissionId, int revision, UUID performedBy, Instant occurredAt,
                            AgreementSnapshot snapshot) {}
