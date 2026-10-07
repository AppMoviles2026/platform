package com.collabtech.platform.campaign.domain.events;

import com.collabtech.platform.shared.domain.events.DomainEvent;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import java.time.Instant;
import java.util.UUID;

public record CampaignClosed(UUID eventId, Instant occurredAt, CampaignId campaignId) implements DomainEvent {}
