package com.collabtech.platform.campaign.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.campaign.domain.model.valueobjects.SpecificationId;

import java.time.Instant;

/** Structural model only. Business transitions are scheduled in docs/implementation-plan.md. */
public final class DeliverableSpecification extends Entity<SpecificationId> {
    private final String contentType;
    private final String description;
    private final int quantity;
    private final Instant deadline;

    public DeliverableSpecification(SpecificationId id, String contentType, String description, int quantity, Instant deadline) {
        super(id);
        this.contentType = contentType;
        this.description = description;
        this.quantity = quantity;
        this.deadline = deadline;
    }

    public String contentType() { return contentType; }
    public String description() { return description; }
    public int quantity() { return quantity; }
    public Instant deadline() { return deadline; }
}
