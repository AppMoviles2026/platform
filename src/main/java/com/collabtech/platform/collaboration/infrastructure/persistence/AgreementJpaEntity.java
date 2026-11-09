package com.collabtech.platform.collaboration.infrastructure.persistence;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="collaboration_agreement")
public class AgreementJpaEntity {
    @Id @Column(name="agreement_id",columnDefinition="char(36)") String id;
    @Column(name="application_id",columnDefinition="char(36)",nullable=false,unique=true) String applicationId;
    @Column(name="brand_id",columnDefinition="char(36)",nullable=false) String brandId;
    @Column(name="creator_id",columnDefinition="char(36)",nullable=false) String creatorId;
    @Column(columnDefinition="longtext",nullable=false) String snapshot;
    @Column(length=16,nullable=false) String status;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="acceptance_deadline",columnDefinition="datetime(6)",nullable=false) Instant deadline;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="created_at",columnDefinition="datetime(6)",nullable=false) Instant createdAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="brand_accepted_at",columnDefinition="datetime(6)") Instant brandAt;
    @Column(name="brand_accepted_by",columnDefinition="char(36)") String brandBy;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="creator_accepted_at",columnDefinition="datetime(6)") Instant creatorAt;
    @Column(name="creator_accepted_by",columnDefinition="char(36)") String creatorBy;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="decided_at",columnDefinition="datetime(6)") Instant decidedAt;
    @Column(name="rejected_by",columnDefinition="char(36)") String rejectedBy;
    @Version @Column(nullable=false) long version;
    protected AgreementJpaEntity(){}
}
