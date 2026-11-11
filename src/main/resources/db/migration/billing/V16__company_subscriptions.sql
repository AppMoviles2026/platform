CREATE TABLE billing_plan (
 plan_id CHAR(36) NOT NULL PRIMARY KEY,code VARCHAR(40) NOT NULL,definition_hash CHAR(64) NOT NULL UNIQUE,
 available BOOLEAN NOT NULL,demonstration BOOLEAN NOT NULL,definition_json TEXT NOT NULL
);
CREATE INDEX idx_billing_plan_available ON billing_plan(available,code,plan_id);
CREATE TABLE billing_subscription (
 subscription_id CHAR(36) NOT NULL PRIMARY KEY,payment_account_id CHAR(36) NOT NULL,plan_id CHAR(36) NOT NULL,
 method_id CHAR(36) NOT NULL,request_id CHAR(36) NOT NULL,request_hash CHAR(64) NOT NULL,
 current_slot VARCHAR(8),provider_hash CHAR(64) UNIQUE,state_hash CHAR(64) NOT NULL UNIQUE,
 next_check_at DATETIME(6) NOT NULL,status VARCHAR(40) NOT NULL,created_at DATETIME(6) NOT NULL,version BIGINT NOT NULL,state_json TEXT NOT NULL,
 CONSTRAINT fk_subscription_account FOREIGN KEY(payment_account_id) REFERENCES billing_account(payment_account_id),
 CONSTRAINT fk_subscription_plan FOREIGN KEY(plan_id) REFERENCES billing_plan(plan_id),
 CONSTRAINT fk_subscription_method FOREIGN KEY(method_id) REFERENCES billing_method(method_id),
 CONSTRAINT uk_subscription_request UNIQUE(payment_account_id,request_id),
 CONSTRAINT uk_subscription_current UNIQUE(payment_account_id,current_slot)
);
CREATE INDEX idx_subscription_history ON billing_subscription(payment_account_id,created_at,subscription_id);
CREATE TABLE billing_subscription_payment (
 payment_hash CHAR(64) NOT NULL PRIMARY KEY,subscription_id CHAR(36) NOT NULL,
 encrypted_reference TEXT NOT NULL,status VARCHAR(32) NOT NULL,amount DECIMAL(14,2),currency CHAR(3),
 paid_at DATETIME(6),updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_subscription_payment FOREIGN KEY(subscription_id) REFERENCES billing_subscription(subscription_id)
);
CREATE TABLE billing_subscription_event (
 event_id VARCHAR(128) NOT NULL PRIMARY KEY,subscription_hint CHAR(36),encrypted_provider TEXT,provider_hash CHAR(64),
 payment_hash CHAR(64),event_type VARCHAR(128) NOT NULL,status VARCHAR(24) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,next_attempt_at DATETIME(6) NOT NULL,lease_token CHAR(36),error_code VARCHAR(64)
);
CREATE INDEX idx_subscription_event_due ON billing_subscription_event(status,next_attempt_at,event_id);
CREATE INDEX idx_subscription_poll ON billing_subscription(current_slot,next_check_at,subscription_id);
