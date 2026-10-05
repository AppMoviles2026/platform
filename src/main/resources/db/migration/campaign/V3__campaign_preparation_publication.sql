CREATE TABLE campaign_campaign (
    campaign_id CHAR(36) NOT NULL PRIMARY KEY,
    brand_id CHAR(36) NOT NULL,
    brand_name VARCHAR(150) NOT NULL,
    title VARCHAR(200) NOT NULL,
    objective VARCHAR(2000) NOT NULL,
    description VARCHAR(5000),
    category VARCHAR(100) NOT NULL,
    target_audience VARCHAR(2000) NOT NULL,
    location VARCHAR(150),
    status VARCHAR(16) NOT NULL,
    publication_date TIMESTAMP(6),
    application_deadline TIMESTAMP(6),
    compensation_type VARCHAR(16),
    compensation_amount DECIMAL(12,2),
    compensation_currency CHAR(3),
    compensation_description VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_campaign_status CHECK(status IN ('DRAFT','OPEN','CLOSED','CANCELLED')),
    CONSTRAINT ck_campaign_amount CHECK(compensation_amount IS NULL OR compensation_amount > 0)
);
CREATE INDEX idx_campaign_brand ON campaign_campaign(brand_id);
CREATE INDEX idx_campaign_visibility ON campaign_campaign(status,publication_date,campaign_id);

CREATE TABLE campaign_requirement (
    requirement_id CHAR(36) NOT NULL PRIMARY KEY,
    campaign_id CHAR(36) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    mandatory BOOLEAN NOT NULL,
    CONSTRAINT fk_requirement_campaign FOREIGN KEY(campaign_id) REFERENCES campaign_campaign(campaign_id)
);
CREATE TABLE campaign_deliverable_specification (
    specification_id CHAR(36) NOT NULL PRIMARY KEY,
    campaign_id CHAR(36) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    quantity INT NOT NULL,
    deadline TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_specification_campaign FOREIGN KEY(campaign_id) REFERENCES campaign_campaign(campaign_id),
    CONSTRAINT ck_specification_quantity CHECK(quantity BETWEEN 1 AND 1000)
);
