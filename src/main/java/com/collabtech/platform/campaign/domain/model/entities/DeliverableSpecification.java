package com.collabtech.platform.campaign.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.campaign.domain.model.valueobjects.SpecificationId;

import java.time.Instant;

/** Expected content specification; not an executed Collaboration deliverable. */
public final class DeliverableSpecification extends Entity<SpecificationId> {
    private final String contentType;
    private final String description;
    private final int quantity;
    private final Instant deadline;

    public DeliverableSpecification(SpecificationId id, String contentType, String description, int quantity, Instant deadline) {
        super(id);
        this.contentType = com.collabtech.platform.campaign.domain.model.valueobjects.CampaignText.required(contentType, 100);
        this.description = com.collabtech.platform.campaign.domain.model.valueobjects.CampaignText.required(description, 2000);
        if (quantity < 1 || quantity > 1000 || deadline == null) throw new IllegalArgumentException("Quantity 1–1000 and deadline required");
        this.quantity = quantity;
        this.deadline = deadline;
    }

    public String contentType() { return contentType; }
    public String description() { return description; }
    public int quantity() { return quantity; }
    public Instant deadline() { return deadline; }
}
