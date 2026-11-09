package com.collabtech.platform.identity.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.identity.domain.model.valueobjects.BrandProfileId;

/** Business information owned by an enterprise Account. */
public final class BrandProfile extends Entity<BrandProfileId> {
    private String businessName;
    private String description;
    private String category;
    private String location;
    private long version;

    public BrandProfile(BrandProfileId id, String businessName, String description, String category, String location) {
        super(id);
        if (businessName == null || businessName.isBlank() || businessName.strip().length() > 150) {
            throw new IllegalArgumentException("Business name must contain 1 to 150 characters");
        }
        this.businessName = businessName.strip();
        this.description = optional(description, 2000);
        this.category = optional(category, 150);
        this.location = optional(location, 150);
    }

    public String businessName() { return businessName; }
    public String description() { return description; }
    public String category() { return category; }
    public String location() { return location; }
    public long version() { return version; }
    public void restoreVersion(long version) {
        if (version < 0) throw new IllegalArgumentException("Invalid profile version");
        this.version = version;
    }
    public void updateInformation(String name, String description, String category, String location) {
        var validated = new BrandProfile(id(), name, description, category, location);
        this.businessName = validated.businessName;
        this.description = validated.description;
        this.category = validated.category;
        this.location = validated.location;
    }
    private static String optional(String value, int limit) {
        if (value == null) return null;
        String normalized = value.strip();
        if (normalized.length() > limit) throw new IllegalArgumentException("Profile field exceeds maximum length");
        return normalized.isEmpty() ? null : normalized;
    }
}
