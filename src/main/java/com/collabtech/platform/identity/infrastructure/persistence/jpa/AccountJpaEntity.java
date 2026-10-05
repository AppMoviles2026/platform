package com.collabtech.platform.identity.infrastructure.persistence.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "identity_account")
public class AccountJpaEntity {
    @Id @Column(name = "account_id", length = 36, columnDefinition = "CHAR(36)")
    String id;
    @Column(nullable = false, length = 254, unique = true)
    String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    String passwordHash;
    @Column(name = "account_type", nullable = false, length = 16)
    String accountType;
    @Column(nullable = false, length = 16)
    String status;
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    Instant createdAt;
    @Version @Column(nullable = false)
    Long version;
    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    BrandProfileJpaEntity brandProfile;
    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    CreatorProfileJpaEntity creatorProfile;

    protected AccountJpaEntity() {}
}
