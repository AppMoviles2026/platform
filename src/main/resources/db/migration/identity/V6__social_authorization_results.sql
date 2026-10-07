CREATE TABLE identity_oauth_authorization (
    authorization_id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    client VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(64),
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_authorization_account FOREIGN KEY(account_id) REFERENCES identity_account(account_id),
    CONSTRAINT ck_authorization_client CHECK(client IN ('API','ANDROID')),
    CONSTRAINT ck_authorization_status CHECK(status IN ('PENDING','SUCCEEDED','FAILED'))
);
CREATE INDEX idx_authorization_account ON identity_oauth_authorization(account_id);
ALTER TABLE identity_oauth_state ADD COLUMN authorization_id CHAR(36);
ALTER TABLE identity_oauth_state ADD CONSTRAINT fk_state_authorization FOREIGN KEY(authorization_id) REFERENCES identity_oauth_authorization(authorization_id);
