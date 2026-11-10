ALTER TABLE collaboration_collaboration ADD COLUMN open_incident_count INT NOT NULL DEFAULT 0;
ALTER TABLE collaboration_collaboration ADD COLUMN cancelled_at DATETIME(6);
ALTER TABLE collaboration_collaboration ADD COLUMN history_initialized BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE collaboration_collaboration ADD COLUMN history_sequence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE collaboration_collaboration ADD CONSTRAINT ck_collaboration_incident_count CHECK(open_incident_count>=0);
ALTER TABLE collaboration_deliverable ADD COLUMN correction_deadline_override DATETIME(6);
CREATE TABLE collaboration_incident (
 incident_id CHAR(36) NOT NULL PRIMARY KEY,
 collaboration_id CHAR(36) NOT NULL,
 opened_by CHAR(36) NOT NULL,
 category VARCHAR(24) NOT NULL,
 description VARCHAR(4000) NOT NULL,
 deliverable_id CHAR(36),
 evidence LONGTEXT NOT NULL,
 status VARCHAR(24) NOT NULL,
 opened_at DATETIME(6) NOT NULL,
 resolved_at DATETIME(6),
 current_resolution_id CHAR(36),
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_incident_collaboration FOREIGN KEY(collaboration_id) REFERENCES collaboration_collaboration(collaboration_id),
 CONSTRAINT fk_incident_deliverable FOREIGN KEY(deliverable_id) REFERENCES collaboration_deliverable(deliverable_id),
 CONSTRAINT ck_incident_status CHECK(status IN ('OPEN','RESOLUTION_PROPOSED','RESOLVED')),
 CONSTRAINT ck_incident_category CHECK(category IN ('CONDITIONS','DELIVERABLE','COMPENSATION','OTHER'))
);
CREATE INDEX idx_incident_collaboration ON collaboration_incident(collaboration_id,status,opened_at,incident_id);
CREATE TABLE collaboration_resolution (
 resolution_id CHAR(36) NOT NULL PRIMARY KEY,
 incident_id CHAR(36) NOT NULL,
 terms LONGTEXT NOT NULL,
 proposed_by CHAR(36) NOT NULL,
 proposed_at DATETIME(6) NOT NULL,
 status VARCHAR(16) NOT NULL,
 decided_by CHAR(36),
 decided_at DATETIME(6),
 decision_reason VARCHAR(2000),
 CONSTRAINT fk_resolution_incident FOREIGN KEY(incident_id) REFERENCES collaboration_incident(incident_id),
 CONSTRAINT ck_resolution_status CHECK(status IN ('PENDING','ACCEPTED','REJECTED','WITHDRAWN'))
);
CREATE INDEX idx_resolution_incident ON collaboration_resolution(incident_id,proposed_at,resolution_id);
CREATE TABLE collaboration_incident_asset (
 incident_id CHAR(36) NOT NULL,
 asset_id CHAR(36) NOT NULL,
 PRIMARY KEY(incident_id,asset_id),
 CONSTRAINT fk_incident_asset_incident FOREIGN KEY(incident_id) REFERENCES collaboration_incident(incident_id),
 CONSTRAINT fk_incident_asset_asset FOREIGN KEY(asset_id) REFERENCES collaboration_asset(asset_id)
);
CREATE INDEX idx_incident_asset_reverse ON collaboration_incident_asset(asset_id);
CREATE TABLE collaboration_correction_extension (
 extension_id CHAR(36) NOT NULL PRIMARY KEY,
 deliverable_id CHAR(36) NOT NULL,
 submission_id CHAR(36) NOT NULL,
 resolution_id CHAR(36) NOT NULL UNIQUE,
 previous_deadline DATETIME(6) NOT NULL,
 deadline DATETIME(6) NOT NULL,
 proposed_by CHAR(36) NOT NULL,
 accepted_by CHAR(36) NOT NULL,
 accepted_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_extension_deliverable FOREIGN KEY(deliverable_id) REFERENCES collaboration_deliverable(deliverable_id),
 CONSTRAINT fk_extension_submission FOREIGN KEY(submission_id) REFERENCES collaboration_submission(submission_id),
 CONSTRAINT fk_extension_resolution FOREIGN KEY(resolution_id) REFERENCES collaboration_resolution(resolution_id)
);
CREATE INDEX idx_extension_submission ON collaboration_correction_extension(submission_id,accepted_at,extension_id);
CREATE TABLE collaboration_history (
 history_id CHAR(36) NOT NULL PRIMARY KEY,
 collaboration_id CHAR(36) NOT NULL,
 sequence_number BIGINT NOT NULL,
 event_type VARCHAR(64) NOT NULL,
 actor_account_id CHAR(36),
 occurred_at DATETIME(6) NOT NULL,
 source_id CHAR(36) NOT NULL,
 reconstructed BOOLEAN NOT NULL,
 details LONGTEXT NOT NULL,
 CONSTRAINT fk_history_collaboration FOREIGN KEY(collaboration_id) REFERENCES collaboration_collaboration(collaboration_id),
 CONSTRAINT uk_history_sequence UNIQUE(collaboration_id,sequence_number)
);
CREATE INDEX idx_history_collaboration ON collaboration_history(collaboration_id,sequence_number);
