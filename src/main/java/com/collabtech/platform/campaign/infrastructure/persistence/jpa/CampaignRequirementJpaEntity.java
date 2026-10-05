package com.collabtech.platform.campaign.infrastructure.persistence.jpa;
import jakarta.persistence.*;
@Entity @Table(name="campaign_requirement")
public class CampaignRequirementJpaEntity {
    @Id @Column(name="requirement_id", columnDefinition="CHAR(36)") String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="campaign_id", nullable=false) CampaignJpaEntity campaign;
    @Column(nullable=false, length=2000) String description;
    @Column(nullable=false) boolean mandatory;
    @Column(name="rule_type", nullable=false, length=32) String ruleType;
    @Column(name="expected_value", length=150) String expectedValue;
    protected CampaignRequirementJpaEntity() {}
}
