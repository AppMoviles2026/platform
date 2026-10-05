ALTER TABLE campaign_requirement ADD COLUMN rule_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL_CONFIRMATION';
ALTER TABLE campaign_requirement ADD COLUMN expected_value VARCHAR(150);
ALTER TABLE campaign_requirement ADD CONSTRAINT ck_requirement_rule CHECK(rule_type IN ('MANUAL_CONFIRMATION','NICHE_EQUALS','LOCATION_EQUALS','AUTHORIZED_PLATFORM'));

CREATE TABLE campaign_application (
    application_id CHAR(36) NOT NULL PRIMARY KEY,
    campaign_id CHAR(36) NOT NULL,
    creator_id CHAR(36) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_application_campaign FOREIGN KEY(campaign_id) REFERENCES campaign_campaign(campaign_id),
    CONSTRAINT uk_application_campaign_creator UNIQUE(campaign_id,creator_id),
    CONSTRAINT ck_application_status CHECK(status IN ('PENDING','SELECTED','REJECTED','CANCELLED'))
);
CREATE INDEX idx_application_creator ON campaign_application(creator_id,submitted_at,application_id);
CREATE TABLE campaign_application_confirmation (
    application_id CHAR(36) NOT NULL,
    requirement_id CHAR(36) NOT NULL,
    PRIMARY KEY(application_id,requirement_id),
    CONSTRAINT fk_confirmation_application FOREIGN KEY(application_id) REFERENCES campaign_application(application_id),
    CONSTRAINT fk_confirmation_requirement FOREIGN KEY(requirement_id) REFERENCES campaign_requirement(requirement_id)
);
