CREATE TABLE identity_account (
    account_id CHAR(36) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id),
    CONSTRAINT uk_account_email UNIQUE (email),
    CONSTRAINT ck_account_type CHECK (account_type IN ('BRAND', 'CREATOR')),
    CONSTRAINT ck_account_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'DISABLED'))
);

CREATE TABLE identity_brand_profile (
    profile_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    business_name VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    category VARCHAR(150),
    location VARCHAR(150),
    PRIMARY KEY (profile_id),
    CONSTRAINT uk_brand_account UNIQUE (account_id),
    CONSTRAINT fk_brand_account FOREIGN KEY (account_id) REFERENCES identity_account (account_id)
);

CREATE TABLE identity_creator_profile (
    profile_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    biography VARCHAR(2000),
    niche VARCHAR(150),
    audience_description VARCHAR(2000),
    location VARCHAR(150),
    PRIMARY KEY (profile_id),
    CONSTRAINT uk_creator_account UNIQUE (account_id),
    CONSTRAINT fk_creator_account FOREIGN KEY (account_id) REFERENCES identity_account (account_id)
);
