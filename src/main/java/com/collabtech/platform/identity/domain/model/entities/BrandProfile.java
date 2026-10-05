package com.collabtech.platform.identity.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.identity.domain.model.valueobjects.BrandProfileId;

/** Structural model only. Business transitions are scheduled in docs/implementation-plan.md. */
public final class BrandProfile extends Entity<BrandProfileId> {
    private final String businessName;
    private final String description;
    private final String category;
    private final String location;

    public BrandProfile(BrandProfileId id, String businessName, String description, String category, String location) {
        super(id);
        if (businessName == null || businessName.isBlank() || businessName.strip().length() > 150) {
            throw new IllegalArgumentException("Business name must contain 1 to 150 characters");
        }
        this.businessName = businessName.strip();
        this.description = description;
        this.category = category;
        this.location = location;
    }

    public String businessName() { return businessName; }
    public String description() { return description; }
    public String category() { return category; }
    public String location() { return location; }
}
