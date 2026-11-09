package com.collabtech.platform.collaboration.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Immutable terms from a selected application. No foreign aggregates or provider credentials. */
public record AgreementSnapshot(UUID termsVersion,UUID applicationId,long applicationVersion,Instant selectedAt,
        UUID campaignId,long campaignVersion,UUID brandId,UUID creatorId,String brandName,String creatorName,
        String title,String objective,String description,String category,String targetAudience,String location,String proposal,
        Set<UUID> confirmedRequirements,List<Requirement> requirements,List<DeliverableTerm> deliverables,
        Compensation compensation,Instant applicationDeadline,Instant capturedAt,Instant acceptanceDeadline) {
    public AgreementSnapshot {
        Objects.requireNonNull(termsVersion);Objects.requireNonNull(applicationId);Objects.requireNonNull(campaignId);
        Objects.requireNonNull(brandId);Objects.requireNonNull(creatorId);Objects.requireNonNull(compensation);
        Objects.requireNonNull(capturedAt);Objects.requireNonNull(acceptanceDeadline);
        requirements=List.copyOf(requirements);deliverables=List.copyOf(deliverables);confirmedRequirements=Set.copyOf(confirmedRequirements);
        if(deliverables.isEmpty()||applicationVersion<0||campaignVersion<0)throw new IllegalArgumentException("Invalid agreement snapshot");
        if(deliverables.stream().map(DeliverableTerm::id).distinct().count()!=deliverables.size())throw new IllegalArgumentException("Duplicate deliverable");
        var first=deliverables.stream().map(DeliverableTerm::deadline).min(Comparator.naturalOrder()).orElseThrow();
        if(acceptanceDeadline.isAfter(first))throw new IllegalArgumentException("Acceptance cannot outlive the first delivery deadline");
    }
    public record Requirement(UUID id,String description,boolean mandatory,String ruleType,String expectedValue) {}
    public record DeliverableTerm(UUID id,String contentType,String description,int quantity,Instant deadline) {
        public DeliverableTerm {Objects.requireNonNull(id);Objects.requireNonNull(deadline);if(quantity<1||quantity>1000)throw new IllegalArgumentException("Invalid quantity");}
    }
    public record Compensation(String type,BigDecimal amount,String currency,String description) {}
}
