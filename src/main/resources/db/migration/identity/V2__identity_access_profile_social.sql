CREATE TABLE identity_access_session (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_session_account FOREIGN KEY (account_id) REFERENCES identity_account(account_id)
);
CREATE INDEX idx_session_account ON identity_access_session(account_id);

CREATE TABLE identity_recovery_token (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_recovery_account FOREIGN KEY (account_id) REFERENCES identity_account(account_id)
);
CREATE INDEX idx_recovery_account ON identity_recovery_token(account_id);

CREATE TABLE identity_recovery_mail (
    mail_id CHAR(36) NOT NULL PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    encrypted_token VARCHAR(2000),
    expires_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6),
    attempts INT NOT NULL DEFAULT 0
);

CREATE TABLE identity_oauth_state (
    state_hash CHAR(64) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_state_account FOREIGN KEY (account_id) REFERENCES identity_account(account_id)
);

CREATE TABLE identity_social_account (
    social_id CHAR(36) NOT NULL PRIMARY KEY,
    profile_id CHAR(36) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    external_account_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT uk_profile_social UNIQUE(profile_id, platform, external_account_id),
    CONSTRAINT fk_social_profile FOREIGN KEY(profile_id) REFERENCES identity_creator_profile(profile_id),
    CONSTRAINT ck_social_status CHECK(status IN ('ACTIVE', 'REVOKED'))
);

CREATE TABLE identity_provider_credentials (
    credential_id CHAR(36) NOT NULL PRIMARY KEY,
    social_id CHAR(36),
    encrypted_access_token VARCHAR(4000) NOT NULL,
    encrypted_refresh_token VARCHAR(4000),
    scopes VARCHAR(2000) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_credentials_social UNIQUE(social_id),
    CONSTRAINT fk_credentials_social FOREIGN KEY(social_id) REFERENCES identity_social_account(social_id)
);
