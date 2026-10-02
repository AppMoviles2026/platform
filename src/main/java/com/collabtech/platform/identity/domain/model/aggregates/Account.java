package com.collabtech.platform.identity.domain.model.aggregates;

import com.collabtech.platform.shared.domain.model.AggregateRoot;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountType;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountStatus;
import com.collabtech.platform.identity.domain.model.entities.BrandProfile;
import com.collabtech.platform.identity.domain.model.entities.CreatorProfile;
import java.time.Instant;

/** Structural model only. Business transitions are scheduled in docs/implementation-plan.md. */
public final class Account extends AggregateRoot<AccountId> {
    private final EmailAddress email;
    private final String passwordHash;
    private final AccountType accountType;
    private final AccountStatus status;
    private final Instant createdAt;
    private final BrandProfile brandProfile;
    private final CreatorProfile creatorProfile;

    public Account(AccountId id, EmailAddress email, String passwordHash, AccountType accountType, AccountStatus status, Instant createdAt, BrandProfile brandProfile, CreatorProfile creatorProfile) {
        super(id);
        java.util.Objects.requireNonNull(email, "email");
        java.util.Objects.requireNonNull(accountType, "accountType");
        java.util.Objects.requireNonNull(status, "status");
        java.util.Objects.requireNonNull(createdAt, "createdAt");
        java.util.Objects.requireNonNull(passwordHash, "passwordHash");
        if ((accountType == AccountType.BRAND && (brandProfile == null || creatorProfile != null))
                || (accountType == AccountType.CREATOR && (creatorProfile == null || brandProfile != null))) {
            throw new IllegalArgumentException("Account must own exactly one matching profile");
        }
        this.email = email;
        this.passwordHash = passwordHash;
        this.accountType = accountType;
        this.status = status;
        this.createdAt = createdAt;
        this.brandProfile = brandProfile;
        this.creatorProfile = creatorProfile;
    }

    public EmailAddress email() { return email; }
    public String passwordHash() { return passwordHash; }
    public AccountType accountType() { return accountType; }
    public AccountStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public BrandProfile brandProfile() { return brandProfile; }
    public CreatorProfile creatorProfile() { return creatorProfile; }
}
