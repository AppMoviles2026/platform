package com.collabtech.platform.collaboration.infrastructure.persistence;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="collaboration_collaboration")
public class CollaborationJpaEntity {
    @Id @Column(name="collaboration_id",columnDefinition="char(36)") String id;
    @Column(name="agreement_id",columnDefinition="char(36)",nullable=false,unique=true) String agreementId;
    @Column(name="brand_id",columnDefinition="char(36)",nullable=false) String brandId;
    @Column(name="creator_id",columnDefinition="char(36)",nullable=false) String creatorId;
    @Column(columnDefinition="longtext",nullable=false) String snapshot;
    @Column(columnDefinition="longtext",nullable=false) String obligations;
    @Column(length=16,nullable=false) String status;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="started_at",columnDefinition="datetime(6)",nullable=false) Instant startedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="next_deadline",columnDefinition="datetime(6)") Instant deadline;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name="expired_at",columnDefinition="datetime(6)") Instant expiredAt;
    @Version @Column(nullable=false) long version;
    protected CollaborationJpaEntity(){}
}
