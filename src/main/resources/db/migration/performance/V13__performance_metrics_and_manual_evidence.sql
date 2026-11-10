CREATE TABLE performance_report (
 report_id CHAR(36) NOT NULL PRIMARY KEY,
 collaboration_id CHAR(36) NOT NULL UNIQUE,
 brand_id CHAR(36) NOT NULL,creator_id CHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE performance_publication (
 publication_id CHAR(36) NOT NULL PRIMARY KEY,report_id CHAR(36) NOT NULL,
 social_id CHAR(36) NOT NULL,platform VARCHAR(32) NOT NULL,post_id VARCHAR(128) NOT NULL,created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_performance_publication_report FOREIGN KEY(report_id) REFERENCES performance_report(report_id),
 CONSTRAINT uk_performance_publication UNIQUE(report_id,social_id,post_id)
);
CREATE TABLE performance_binding (
 publication_id CHAR(36) NOT NULL,submission_id CHAR(36) NOT NULL,deliverable_id CHAR(36) NOT NULL,
 publication_url VARCHAR(2048) NOT NULL,bound_at DATETIME(6) NOT NULL,
 PRIMARY KEY(publication_id,submission_id),
 CONSTRAINT fk_performance_binding_publication FOREIGN KEY(publication_id) REFERENCES performance_publication(publication_id)
);
CREATE TABLE performance_snapshot (
 snapshot_id CHAR(36) NOT NULL PRIMARY KEY,publication_id CHAR(36) NOT NULL,submission_id CHAR(36) NOT NULL,
 captured_at DATETIME(6) NOT NULL,period_start DATETIME(6) NOT NULL,period_end DATETIME(6) NOT NULL,
 metrics LONGTEXT NOT NULL,recorded_sequence BIGINT NOT NULL,
 CONSTRAINT fk_performance_snapshot_publication FOREIGN KEY(publication_id) REFERENCES performance_publication(publication_id),
 CONSTRAINT uk_performance_observation UNIQUE(publication_id,recorded_sequence)
);
CREATE INDEX idx_performance_snapshot ON performance_snapshot(publication_id,captured_at,snapshot_id);
CREATE TABLE performance_refresh (
 refresh_id CHAR(36) NOT NULL PRIMARY KEY,publication_id CHAR(36) NOT NULL,requested_by CHAR(36) NOT NULL,
 checked_at DATETIME(6) NOT NULL,recorded_sequence BIGINT NOT NULL,status VARCHAR(40) NOT NULL,snapshot_id CHAR(36),
 CONSTRAINT fk_performance_refresh_publication FOREIGN KEY(publication_id) REFERENCES performance_publication(publication_id),
 CONSTRAINT fk_performance_refresh_snapshot FOREIGN KEY(snapshot_id) REFERENCES performance_snapshot(snapshot_id)
);
CREATE INDEX idx_performance_refresh ON performance_refresh(publication_id,checked_at,refresh_id);
CREATE TABLE performance_asset (
 asset_id CHAR(36) NOT NULL PRIMARY KEY,report_id CHAR(36) NOT NULL,owner_account_id CHAR(36) NOT NULL,
 filename VARCHAR(150) NOT NULL,media_type VARCHAR(64) NOT NULL,byte_size BIGINT NOT NULL,sha256 CHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL,bytes LONGBLOB NOT NULL,
 CONSTRAINT fk_performance_asset_report FOREIGN KEY(report_id) REFERENCES performance_report(report_id)
);
CREATE TABLE performance_evidence (
 evidence_id CHAR(36) NOT NULL PRIMARY KEY,report_id CHAR(36) NOT NULL,submitted_by CHAR(36) NOT NULL,
 submission_id CHAR(36),description VARCHAR(4000) NOT NULL,references_json LONGTEXT NOT NULL,claimed_metrics LONGTEXT NOT NULL,
 observed_at DATETIME(6) NOT NULL,period_start DATETIME(6) NOT NULL,period_end DATETIME(6) NOT NULL,submitted_at DATETIME(6) NOT NULL,
 status VARCHAR(16) NOT NULL,is_demonstration BOOLEAN NOT NULL DEFAULT FALSE,version BIGINT NOT NULL DEFAULT 0,
 reviewed_by CHAR(36),reviewed_at DATETIME(6),observations VARCHAR(2000),
 CONSTRAINT fk_performance_evidence_report FOREIGN KEY(report_id) REFERENCES performance_report(report_id),
 CONSTRAINT ck_performance_evidence_status CHECK(status IN ('UNVERIFIED','VERIFIED','REJECTED'))
);
CREATE INDEX idx_performance_evidence ON performance_evidence(report_id,submitted_at,evidence_id);
CREATE TABLE performance_evidence_asset (
 evidence_id CHAR(36) NOT NULL,asset_id CHAR(36) NOT NULL,PRIMARY KEY(evidence_id,asset_id),
 CONSTRAINT fk_performance_evidence_asset_evidence FOREIGN KEY(evidence_id) REFERENCES performance_evidence(evidence_id),
 CONSTRAINT fk_performance_evidence_asset_asset FOREIGN KEY(asset_id) REFERENCES performance_asset(asset_id)
);
CREATE TABLE performance_command (
 report_id CHAR(36) NOT NULL,actor_id CHAR(36) NOT NULL,request_id CHAR(36) NOT NULL,
 operation VARCHAR(32) NOT NULL,fingerprint CHAR(64) NOT NULL,result_id CHAR(36) NOT NULL,created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(report_id,actor_id,request_id),
 CONSTRAINT fk_performance_command_report FOREIGN KEY(report_id) REFERENCES performance_report(report_id)
);
