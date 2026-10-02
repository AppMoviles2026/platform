package com.collabtech.platform.identity.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.identity.domain.model.valueobjects.CreatorProfileId;

import java.util.List;

/** Structural model only. Business transitions are scheduled in docs/implementation-plan.md. */
public final class CreatorProfile extends Entity<CreatorProfileId> {
    private final String displayName;
    private final String biography;
    private final String niche;
    private final String audienceDescription;
    private final String location;
    private final List<SocialMediaAccount> socialMediaAccounts;

    public CreatorProfile(CreatorProfileId id, String displayName, String biography, String niche, String audienceDescription, String location, List<SocialMediaAccount> socialMediaAccounts) {
        super(id);
        this.displayName = displayName;
        this.biography = biography;
        this.niche = niche;
        this.audienceDescription = audienceDescription;
        this.location = location;
        this.socialMediaAccounts = List.copyOf(socialMediaAccounts);
    }

    public String displayName() { return displayName; }
    public String biography() { return biography; }
    public String niche() { return niche; }
    public String audienceDescription() { return audienceDescription; }
    public String location() { return location; }
    public List<SocialMediaAccount> socialMediaAccounts() { return socialMediaAccounts; }
}
