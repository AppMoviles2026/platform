package com.collabtech.platform.collaboration.domain.model;

import java.time.Instant;
import java.util.UUID;

public record IncidentEvent(UUID eventId, UUID collaborationId, String type, UUID incidentId, UUID resolutionId,
                            UUID performedBy, Instant occurredAt, int openIncidentCount,
                            Collaboration.Status collaborationStatus, Incident.Terms resolution) {}
