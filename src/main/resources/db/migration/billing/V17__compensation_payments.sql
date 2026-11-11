CREATE TABLE billing_compensation (
 compensation_id CHAR(36) NOT NULL PRIMARY KEY,collaboration_id CHAR(36) NOT NULL UNIQUE,
 brand_profile_id CHAR(36) NOT NULL,creator_profile_id CHAR(36) NOT NULL,terms_hash CHAR(64) NOT NULL,
 next_check_at DATETIME(6) NOT NULL,version BIGINT NOT NULL,state_json LONGTEXT NOT NULL
);
CREATE INDEX idx_compensation_poll ON billing_compensation(next_check_at,compensation_id);
CREATE TABLE billing_compensation_request (
 compensation_id CHAR(36) NOT NULL,request_id CHAR(36) NOT NULL,action VARCHAR(24) NOT NULL,request_hash CHAR(64) NOT NULL,
 operation_id CHAR(36) NOT NULL,created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(compensation_id,request_id),
 CONSTRAINT fk_comp_request_root FOREIGN KEY(compensation_id) REFERENCES billing_compensation(compensation_id)
);
CREATE TABLE billing_compensation_reference (
 reference_hash CHAR(64) NOT NULL PRIMARY KEY,compensation_id CHAR(36) NOT NULL,operation_id CHAR(36) NOT NULL,
 kind VARCHAR(24) NOT NULL,encrypted_reference TEXT NOT NULL,
 CONSTRAINT fk_comp_reference_root FOREIGN KEY(compensation_id) REFERENCES billing_compensation(compensation_id)
);
CREATE TABLE billing_compensation_event (
 event_id VARCHAR(128) NOT NULL PRIMARY KEY,compensation_hint CHAR(36),reference_hash CHAR(64),encrypted_hint TEXT,
 status VARCHAR(24) NOT NULL,attempts INT NOT NULL DEFAULT 0,next_attempt_at DATETIME(6) NOT NULL,
 lease_token CHAR(36),error_code VARCHAR(64)
);
CREATE INDEX idx_comp_event_due ON billing_compensation_event(status,next_attempt_at,event_id);
CREATE TABLE billing_compensation_audit (
 audit_id CHAR(36) NOT NULL PRIMARY KEY,compensation_id CHAR(36) NOT NULL,operation_id CHAR(36),actor_account_id CHAR(36),
 event_type VARCHAR(64) NOT NULL,occurred_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_comp_audit_root FOREIGN KEY(compensation_id) REFERENCES billing_compensation(compensation_id)
);
