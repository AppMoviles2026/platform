package com.collabtech.platform.identity.domain.model.entities;

import com.collabtech.platform.shared.domain.model.Entity;
import com.collabtech.platform.identity.domain.model.valueobjects.CreatorProfileId;

import java.util.List;
import java.util.ArrayList;
import com.collabtech.platform.identity.domain.exceptions.DuplicateSocialAccountException;

/** Creator information and authorized social identities, owned by the Account aggregate. */
public final class CreatorProfile extends Entity<CreatorProfileId> {
    private String displayName;
    private String biography;
    private String niche;
    private String audienceDescription;
    private String location;
    private final List<SocialMediaAccount> socialMediaAccounts;

    public CreatorProfile(CreatorProfileId id, String displayName, String biography, String niche, String audienceDescription, String location, List<SocialMediaAccount> socialMediaAccounts) {
        super(id);
        if (displayName == null || displayName.isBlank() || displayName.strip().length() > 150) {
            throw new IllegalArgumentException("Display name must contain 1 to 150 characters");
        }
        this.displayName = displayName.strip();
        this.biography = optional(biography, 2000);
        this.niche = optional(niche, 150);
        this.audienceDescription = optional(audienceDescription, 2000);
        this.location = optional(location, 150);
        this.socialMediaAccounts = new ArrayList<>(List.copyOf(socialMediaAccounts));
    }

    public String displayName() { return displayName; }
    public String biography() { return biography; }
    public String niche() { return niche; }
    public String audienceDescription() { return audienceDescription; }
    public String location() { return location; }
    public List<SocialMediaAccount> socialMediaAccounts() { return List.copyOf(socialMediaAccounts); }

    public void updateInformation(String name, String biography, String niche, String audience, String location) {
        var validated = new CreatorProfile(id(), name, biography, niche, audience, location, socialMediaAccounts);
        this.displayName = validated.displayName;
        this.biography = validated.biography;
        this.niche = validated.niche;
        this.audienceDescription = validated.audienceDescription;
        this.location = validated.location;
    }

    public void linkSocialMediaAccount(SocialMediaAccount social) {
        java.util.Objects.requireNonNull(social);
        if (socialMediaAccounts.stream().anyMatch(existing -> existing.platform().equals(social.platform())
                && existing.externalAccountId().equals(social.externalAccountId()))) {
            throw new DuplicateSocialAccountException();
        }
        socialMediaAccounts.add(social);
    }

    private static String optional(String value, int limit) {
        if (value != null && value.strip().length() > limit) throw new IllegalArgumentException("Profile field exceeds maximum length");
        return value == null ? null : value.strip();
    }
}
