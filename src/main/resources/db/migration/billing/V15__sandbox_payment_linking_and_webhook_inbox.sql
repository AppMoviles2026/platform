CREATE TABLE billing_account (
 payment_account_id CHAR(36) NOT NULL PRIMARY KEY, account_id CHAR(36) NOT NULL UNIQUE,
 role VARCHAR(16) NOT NULL, created_at DATETIME(6) NOT NULL,version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE billing_authorization (
 authorization_id CHAR(36) NOT NULL PRIMARY KEY,payment_account_id CHAR(36) NOT NULL,
 request_id CHAR(36) NOT NULL,request_hash CHAR(64) NOT NULL,purpose VARCHAR(16) NOT NULL,client VARCHAR(16) NOT NULL,
 state_hash CHAR(64) NOT NULL UNIQUE,encrypted_state TEXT,status VARCHAR(32) NOT NULL,
 created_at DATETIME(6) NOT NULL,expires_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,
 encrypted_setup_id TEXT,setup_hash CHAR(64),encrypted_approval_url TEXT,encrypted_customer_id TEXT,
 completion_hash CHAR(64),encrypted_access TEXT,access_expires_at DATETIME(6),
 method_id CHAR(36),error_code VARCHAR(64),version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_billing_auth_account FOREIGN KEY(payment_account_id) REFERENCES billing_account(payment_account_id),
 CONSTRAINT uk_billing_auth_request UNIQUE(payment_account_id,request_id)
);
CREATE TABLE billing_resource (
 resource_hash CHAR(64) NOT NULL PRIMARY KEY,revoked BOOLEAN NOT NULL DEFAULT FALSE,updated_at DATETIME(6) NOT NULL
);
CREATE TABLE billing_method (
 method_id CHAR(36) NOT NULL PRIMARY KEY,payment_account_id CHAR(36) NOT NULL,purpose VARCHAR(16) NOT NULL,
 status VARCHAR(32) NOT NULL,provider VARCHAR(16) NOT NULL,environment VARCHAR(16) NOT NULL,
 encrypted_reference TEXT NOT NULL,reference_hash CHAR(64) NOT NULL,
 subject_hash CHAR(64) NOT NULL,masked_label VARCHAR(254) NOT NULL,
 linked_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_billing_method_account FOREIGN KEY(payment_account_id) REFERENCES billing_account(payment_account_id),
 CONSTRAINT fk_billing_method_resource FOREIGN KEY(reference_hash) REFERENCES billing_resource(resource_hash),
 CONSTRAINT uk_billing_subject UNIQUE(purpose,subject_hash),
 CONSTRAINT uk_billing_reference UNIQUE(reference_hash),
 CONSTRAINT ck_billing_environment CHECK(environment='SANDBOX')
);
CREATE INDEX idx_billing_method_owner ON billing_method(payment_account_id,purpose,status,method_id);
CREATE TABLE billing_webhook_event (
 event_id VARCHAR(128) NOT NULL PRIMARY KEY,event_type VARCHAR(128) NOT NULL,resource_hash CHAR(64),
 payload_hash CHAR(64) NOT NULL,encrypted_payload LONGTEXT NOT NULL,status VARCHAR(24) NOT NULL,
 received_at DATETIME(6) NOT NULL,processed_at DATETIME(6),attempts INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_billing_webhook_resource ON billing_webhook_event(resource_hash,event_type);
CREATE TABLE billing_audit (
 audit_id CHAR(36) NOT NULL PRIMARY KEY,payment_account_id CHAR(36),authorization_id CHAR(36),method_id CHAR(36),
 event_type VARCHAR(64) NOT NULL,occurred_at DATETIME(6) NOT NULL
);
