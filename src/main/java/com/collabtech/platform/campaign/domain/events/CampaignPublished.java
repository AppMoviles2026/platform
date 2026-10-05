package com.collabtech.platform.campaign.domain.events;

import com.collabtech.platform.shared.domain.events.DomainEvent;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import java.time.Instant;
import java.util.UUID;

/** Recorded internally when a complete campaign is published. */
public record CampaignPublished(UUID eventId, Instant occurredAt, CampaignId campaignId) implements DomainEvent {}
