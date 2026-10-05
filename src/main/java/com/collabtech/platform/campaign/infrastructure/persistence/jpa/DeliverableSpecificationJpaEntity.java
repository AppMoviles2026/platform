package com.collabtech.platform.campaign.infrastructure.persistence.jpa;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="campaign_deliverable_specification")
public class DeliverableSpecificationJpaEntity {
    @Id @Column(name="specification_id", columnDefinition="CHAR(36)") String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="campaign_id", nullable=false) CampaignJpaEntity campaign;
    @Column(name="content_type", nullable=false, length=100) String contentType;
    @Column(nullable=false, length=2000) String description;
    @Column(nullable=false) int quantity;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
    @Column(nullable=false, columnDefinition="DATETIME(6)") Instant deadline;
    protected DeliverableSpecificationJpaEntity() {}
}
