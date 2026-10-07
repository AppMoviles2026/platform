CREATE TABLE campaign_idempotent_command (
    actor_id CHAR(36) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    response_json MEDIUMTEXT,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY(actor_id, operation, key_hash)
);
CREATE INDEX idx_idempotent_expiry ON campaign_idempotent_command(expires_at);
