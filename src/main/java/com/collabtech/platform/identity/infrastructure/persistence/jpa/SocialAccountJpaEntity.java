package com.collabtech.platform.identity.infrastructure.persistence.jpa;

import jakarta.persistence.*;

@Entity
@Table(name = "identity_social_account")
public class SocialAccountJpaEntity {
    @Id @Column(name = "social_id", length = 36, columnDefinition = "CHAR(36)") String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false) CreatorProfileJpaEntity profile;
    @Column(nullable = false, length = 32) String platform;
    @Column(name = "external_account_id", nullable = false, length = 255) String externalAccountId;
    @Column(nullable = false, length = 255) String username;
    @Column(nullable = false, length = 16) String status;
    protected SocialAccountJpaEntity() {}
}
