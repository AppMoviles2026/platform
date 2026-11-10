CREATE TABLE performance_attribution (
 attribution_id CHAR(36) NOT NULL PRIMARY KEY, report_id CHAR(36) NOT NULL,
 kind VARCHAR(16) NOT NULL, label VARCHAR(120) NOT NULL,
 destination_id VARCHAR(40), destination_url VARCHAR(2048), tracking_token VARCHAR(64), attribution_code VARCHAR(32),
 status VARCHAR(16) NOT NULL, is_demonstration BOOLEAN NOT NULL DEFAULT FALSE,
 created_by CHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_attribution_report FOREIGN KEY(report_id) REFERENCES performance_report(report_id),
 CONSTRAINT uk_attribution_token UNIQUE(tracking_token),
 CONSTRAINT uk_attribution_code UNIQUE(attribution_code),
 CONSTRAINT ck_attribution_kind CHECK(kind IN ('LINK','CODE')),
 CONSTRAINT ck_attribution_status CHECK(status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_attribution_report ON performance_attribution(report_id,created_at,attribution_id);
CREATE TABLE performance_interaction (
 interaction_id CHAR(36) NOT NULL PRIMARY KEY,report_id CHAR(36) NOT NULL,attribution_id CHAR(36) NOT NULL,
 interaction_type VARCHAR(32) NOT NULL,source VARCHAR(32) NOT NULL,event_key CHAR(64) NOT NULL,payload_fingerprint CHAR(64) NOT NULL,
 recorded_by CHAR(36),occurred_at DATETIME(6) NOT NULL,recorded_at DATETIME(6) NOT NULL,evidence_id CHAR(36),
 is_demonstration BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_interaction_report FOREIGN KEY(report_id) REFERENCES performance_report(report_id),
 CONSTRAINT fk_interaction_attribution FOREIGN KEY(attribution_id) REFERENCES performance_attribution(attribution_id),
 CONSTRAINT fk_interaction_evidence FOREIGN KEY(evidence_id) REFERENCES performance_evidence(evidence_id),
 CONSTRAINT uk_interaction_event UNIQUE(report_id,event_key),
 CONSTRAINT ck_interaction_type CHECK(interaction_type IN ('CLICK','INQUIRY','CODE_REDEMPTION')),
 CONSTRAINT ck_interaction_source CHECK(source IN ('FIRST_PARTY_TRACKING','BRAND_DECLARATION'))
);
CREATE INDEX idx_interaction_report_period ON performance_interaction(report_id,occurred_at,interaction_id);
CREATE INDEX idx_interaction_attribution_period ON performance_interaction(attribution_id,occurred_at,interaction_id);
