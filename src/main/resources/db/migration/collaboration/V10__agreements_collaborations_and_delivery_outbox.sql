CREATE TABLE collaboration_agreement (
 agreement_id CHAR(36) NOT NULL PRIMARY KEY,
 application_id CHAR(36) NOT NULL UNIQUE,
 brand_id CHAR(36) NOT NULL,
 creator_id CHAR(36) NOT NULL,
 snapshot LONGTEXT NOT NULL,
 status VARCHAR(16) NOT NULL,
 acceptance_deadline DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 brand_accepted_at DATETIME(6),brand_accepted_by CHAR(36),
 creator_accepted_at DATETIME(6),creator_accepted_by CHAR(36),
 decided_at DATETIME(6),rejected_by CHAR(36),version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_agreement_status CHECK(status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED'))
);
CREATE INDEX idx_agreement_brand ON collaboration_agreement(brand_id,created_at,agreement_id);
CREATE INDEX idx_agreement_creator ON collaboration_agreement(creator_id,created_at,agreement_id);
CREATE INDEX idx_agreement_due ON collaboration_agreement(status,acceptance_deadline);
CREATE TABLE collaboration_collaboration (
 collaboration_id CHAR(36) NOT NULL PRIMARY KEY,agreement_id CHAR(36) NOT NULL UNIQUE,
 brand_id CHAR(36) NOT NULL,creator_id CHAR(36) NOT NULL,snapshot LONGTEXT NOT NULL,
 status VARCHAR(16) NOT NULL,started_at DATETIME(6) NOT NULL,next_deadline DATETIME(6),expired_at DATETIME(6),
 obligations LONGTEXT NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_collaboration_agreement FOREIGN KEY(agreement_id) REFERENCES collaboration_agreement(agreement_id),
 CONSTRAINT ck_collaboration_status CHECK(status IN ('ACTIVE','UNDER_REVIEW','COMPLETED','EXPIRED','CANCELLED','DISPUTED'))
);
CREATE INDEX idx_collaboration_brand ON collaboration_collaboration(brand_id,started_at,collaboration_id);
CREATE INDEX idx_collaboration_creator ON collaboration_collaboration(creator_id,started_at,collaboration_id);
CREATE INDEX idx_collaboration_due ON collaboration_collaboration(status,next_deadline);
CREATE TABLE collaboration_outbox (
 event_id CHAR(36) NOT NULL PRIMARY KEY,aggregate_id CHAR(36) NOT NULL,event_type VARCHAR(64) NOT NULL,
 payload LONGTEXT NOT NULL,occurred_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_collaboration_event UNIQUE(aggregate_id,event_type)
);
CREATE TABLE collaboration_event_receipt (
 event_id CHAR(36) NOT NULL,consumer VARCHAR(64) NOT NULL,delivered BOOLEAN NOT NULL DEFAULT FALSE,
 attempts INT NOT NULL DEFAULT 0,next_attempt_at DATETIME(6),last_error VARCHAR(64),delivered_at DATETIME(6),
 PRIMARY KEY(event_id,consumer),CONSTRAINT fk_collaboration_event FOREIGN KEY(event_id) REFERENCES collaboration_outbox(event_id)
);
