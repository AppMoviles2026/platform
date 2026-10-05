package com.collabtech.platform.campaign.infrastructure.persistence.jpa;
import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="campaign_campaign")
public class CampaignJpaEntity {
    @Id @Column(name="campaign_id", columnDefinition="CHAR(36)") String id;
    @Column(name="brand_id", nullable=false, columnDefinition="CHAR(36)") String brandId;
    @Column(name="brand_name", nullable=false, length=150) String brandName;
    @Column(nullable=false, length=200) String title;
    @Column(nullable=false, length=2000) String objective;
    @Column(length=5000) String description;
    @Column(nullable=false, length=100) String category;
    @Column(name="target_audience", nullable=false, length=2000) String audience;
    @Column(length=150) String location;
    @Column(nullable=false, length=16) String status;
    // DATETIME is timezone-free storage; Hibernate's configured UTC calendar supplies the zone.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
    @Column(name="publication_date", columnDefinition="DATETIME(6)") Instant publicationDate;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
    @Column(name="application_deadline", columnDefinition="DATETIME(6)") Instant applicationDeadline;
    @Column(name="compensation_type", length=16) String compensationType;
    @Column(name="compensation_amount", precision=12, scale=2) BigDecimal amount;
    @Column(name="compensation_currency", columnDefinition="CHAR(3)") String currency;
    @Column(name="compensation_description", length=2000) String compensationDescription;
    @Version @Column(nullable=false) Long version;
    @OneToMany(mappedBy="campaign", cascade=CascadeType.ALL, orphanRemoval=true) @OrderBy("id")
    List<CampaignRequirementJpaEntity> requirements = new ArrayList<>();
    @OneToMany(mappedBy="campaign", cascade=CascadeType.ALL, orphanRemoval=true) @OrderBy("id")
    List<DeliverableSpecificationJpaEntity> deliverables = new ArrayList<>();
    protected CampaignJpaEntity() {}
}
