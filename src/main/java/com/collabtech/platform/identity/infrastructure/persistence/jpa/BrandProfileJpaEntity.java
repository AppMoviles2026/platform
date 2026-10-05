package com.collabtech.platform.identity.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_brand_profile")
public class BrandProfileJpaEntity {
    @Id @Column(name = "profile_id", length = 36, columnDefinition = "CHAR(36)")
    String id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    AccountJpaEntity account;
    @Column(name = "business_name", nullable = false, length = 150)
    String businessName;
    @Column(length = 2000)
    String description;
    @Column(length = 150)
    String category;
    @Column(length = 150)
    String location;

    protected BrandProfileJpaEntity() {}
}
