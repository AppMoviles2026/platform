package com.collabtech.platform.identity.infrastructure.persistence.jpa;

import com.collabtech.platform.identity.domain.model.aggregates.Account;
import com.collabtech.platform.identity.domain.model.entities.BrandProfile;
import com.collabtech.platform.identity.domain.model.entities.CreatorProfile;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountStatus;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountType;
import com.collabtech.platform.identity.domain.model.valueobjects.BrandProfileId;
import com.collabtech.platform.identity.domain.model.valueobjects.CreatorProfileId;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import java.util.List;
import java.util.UUID;
import com.collabtech.platform.identity.domain.model.entities.SocialMediaAccount;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialMediaAccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialAccountStatus;

final class AccountPersistenceMapper {
    private AccountPersistenceMapper() {}

    static AccountJpaEntity toEntity(Account account) {
        var entity = new AccountJpaEntity();
        entity.id = account.id().value().toString();
        entity.email = account.email().value();
        entity.passwordHash = account.passwordHash();
        entity.accountType = account.accountType().name();
        entity.status = account.status().name();
        entity.createdAt = account.createdAt();
        if (account.brandProfile() != null) {
            var profile = account.brandProfile();
            var row = new BrandProfileJpaEntity();
            row.id = profile.id().value().toString();
            row.account = entity;
            row.businessName = profile.businessName();
            row.description = profile.description();
            row.category = profile.category();
            row.location = profile.location();
            entity.brandProfile = row;
        } else {
            var profile = account.creatorProfile();
            var row = new CreatorProfileJpaEntity();
            row.id = profile.id().value().toString();
            row.account = entity;
            row.displayName = profile.displayName();
            row.biography = profile.biography();
            row.niche = profile.niche();
            row.audienceDescription = profile.audienceDescription();
            row.location = profile.location();
            copySocials(profile, row);
            entity.creatorProfile = row;
        }
        return entity;
    }

    static Account toDomain(AccountJpaEntity row) {
        BrandProfile brand = row.brandProfile == null ? null : new BrandProfile(
                new BrandProfileId(UUID.fromString(row.brandProfile.id)), row.brandProfile.businessName,
                row.brandProfile.description, row.brandProfile.category, row.brandProfile.location);
        CreatorProfile creator = row.creatorProfile == null ? null : new CreatorProfile(
                new CreatorProfileId(UUID.fromString(row.creatorProfile.id)), row.creatorProfile.displayName,
                row.creatorProfile.biography, row.creatorProfile.niche, row.creatorProfile.audienceDescription,
                row.creatorProfile.location, row.creatorProfile.socials.stream().map(social -> new SocialMediaAccount(
                        new SocialMediaAccountId(UUID.fromString(social.id)), new SocialPlatform(social.platform),
                        social.externalAccountId, social.username, SocialAccountStatus.valueOf(social.status))).toList());
        return new Account(new AccountId(UUID.fromString(row.id)), new EmailAddress(row.email), row.passwordHash,
                AccountType.valueOf(row.accountType), AccountStatus.valueOf(row.status), row.createdAt, brand, creator);
    }

    static void update(Account account, AccountJpaEntity entity) {
        entity.passwordHash = account.passwordHash();
        if (account.creatorProfile() != null) {
            var profile = account.creatorProfile();
            var row = entity.creatorProfile;
            row.displayName = profile.displayName(); row.biography = profile.biography(); row.niche = profile.niche();
            row.audienceDescription = profile.audienceDescription(); row.location = profile.location();
            copySocials(profile, row);
        }
    }

    private static void copySocials(CreatorProfile profile, CreatorProfileJpaEntity row) {
        for (var social : profile.socialMediaAccounts()) {
            if (row.socials.stream().anyMatch(existing -> existing.id.equals(social.id().value().toString()))) continue;
            var linked = new SocialAccountJpaEntity();
            linked.id = social.id().value().toString(); linked.profile = row; linked.platform = social.platform().code();
            linked.externalAccountId = social.externalAccountId(); linked.username = social.username();
            linked.status = social.status().name(); row.socials.add(linked);
        }
    }
}
