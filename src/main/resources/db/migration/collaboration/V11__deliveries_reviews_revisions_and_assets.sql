CREATE TABLE collaboration_deliverable (
 deliverable_id CHAR(36) NOT NULL PRIMARY KEY,
 collaboration_id CHAR(36) NOT NULL,
 obligation_id CHAR(36) NOT NULL,
 piece_number INT NOT NULL,
 current_revision INT NOT NULL,
 status VARCHAR(24) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_deliverable_collaboration FOREIGN KEY(collaboration_id) REFERENCES collaboration_collaboration(collaboration_id),
 CONSTRAINT uk_deliverable_piece UNIQUE(collaboration_id,obligation_id,piece_number),
 CONSTRAINT ck_deliverable_status CHECK(status IN ('PENDING_VALIDATION','APPROVED','REJECTED')),
 CONSTRAINT ck_deliverable_piece CHECK(piece_number>0 AND current_revision>0)
);
CREATE INDEX idx_deliverable_collaboration ON collaboration_deliverable(collaboration_id,status,created_at,deliverable_id);
CREATE TABLE collaboration_submission (
 submission_id CHAR(36) NOT NULL PRIMARY KEY,
 deliverable_id CHAR(36) NOT NULL,
 revision INT NOT NULL,
 submitted_by CHAR(36) NOT NULL,
 submitted_at DATETIME(6) NOT NULL,
 late BOOLEAN NOT NULL,
 content LONGTEXT NOT NULL,
 CONSTRAINT fk_submission_deliverable FOREIGN KEY(deliverable_id) REFERENCES collaboration_deliverable(deliverable_id),
 CONSTRAINT uk_submission_revision UNIQUE(deliverable_id,revision)
);
CREATE TABLE collaboration_review (
 review_id CHAR(36) NOT NULL PRIMARY KEY,
 submission_id CHAR(36) NOT NULL UNIQUE,
 reviewed_by CHAR(36) NOT NULL,
 reviewed_at DATETIME(6) NOT NULL,
 decision VARCHAR(16) NOT NULL,
 observations VARCHAR(4000),
 correction_deadline DATETIME(6),
 CONSTRAINT fk_review_submission FOREIGN KEY(submission_id) REFERENCES collaboration_submission(submission_id),
 CONSTRAINT ck_review_decision CHECK(decision IN ('APPROVED','REJECTED'))
);
CREATE TABLE collaboration_asset (
 asset_id CHAR(36) NOT NULL PRIMARY KEY,
 collaboration_id CHAR(36) NOT NULL,
 owner_account_id CHAR(36) NOT NULL,
 filename VARCHAR(150) NOT NULL,
 media_type VARCHAR(64) NOT NULL,
 byte_size BIGINT NOT NULL,
 sha256 CHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 bytes LONGBLOB NOT NULL,
 CONSTRAINT fk_asset_collaboration FOREIGN KEY(collaboration_id) REFERENCES collaboration_collaboration(collaboration_id)
);
CREATE INDEX idx_asset_collaboration ON collaboration_asset(collaboration_id,owner_account_id);
CREATE TABLE collaboration_submission_asset (
 submission_id CHAR(36) NOT NULL,
 asset_id CHAR(36) NOT NULL,
 PRIMARY KEY(submission_id,asset_id),
 CONSTRAINT fk_submission_asset_submission FOREIGN KEY(submission_id) REFERENCES collaboration_submission(submission_id),
 CONSTRAINT fk_submission_asset_asset FOREIGN KEY(asset_id) REFERENCES collaboration_asset(asset_id)
);
CREATE INDEX idx_submission_asset_reverse ON collaboration_submission_asset(asset_id);
CREATE TABLE collaboration_delivery_command (
 collaboration_id CHAR(36) NOT NULL,
 actor_account_id CHAR(36) NOT NULL,
 request_id CHAR(36) NOT NULL,
 operation VARCHAR(24) NOT NULL,
 fingerprint CHAR(64) NOT NULL,
 result_id CHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(collaboration_id,actor_account_id,request_id),
 CONSTRAINT fk_delivery_command_collaboration FOREIGN KEY(collaboration_id) REFERENCES collaboration_collaboration(collaboration_id)
);
-- A collaboration can expire again after a correction deadline. Identity is the event UUID,
-- not a globally unique combination of aggregate and event type.
ALTER TABLE collaboration_outbox DROP INDEX uk_collaboration_event;
CREATE INDEX idx_collaboration_event_type ON collaboration_outbox(aggregate_id,event_type);
ALTER TABLE collaboration_collaboration ADD COLUMN completed_at DATETIME(6);
