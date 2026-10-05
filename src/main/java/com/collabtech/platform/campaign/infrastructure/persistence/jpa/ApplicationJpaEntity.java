package com.collabtech.platform.campaign.infrastructure.persistence.jpa;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
@Entity @Table(name="campaign_application")
public class ApplicationJpaEntity {
    @Id @Column(name="application_id",columnDefinition="char(36)") String id;
    @Column(name="campaign_id",columnDefinition="char(36)",nullable=false) String campaignId;
    @Column(name="creator_id",columnDefinition="char(36)",nullable=false) String creatorId;
    @Column(name="message",length=4000,nullable=false) String message;
    @Column(name="status",length=16,nullable=false) String status;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="submitted_at",columnDefinition="datetime(6)",nullable=false) Instant submittedAt;
    @Version @Column(name="version",nullable=false) long version;
    @ElementCollection @CollectionTable(name="campaign_application_confirmation",joinColumns=@JoinColumn(name="application_id",columnDefinition="char(36)"))
    @Column(name="requirement_id",columnDefinition="char(36)") Set<String> confirmations=new HashSet<>();
    protected ApplicationJpaEntity() {}
}
