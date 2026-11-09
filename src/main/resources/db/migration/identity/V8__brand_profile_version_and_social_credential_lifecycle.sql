ALTER TABLE identity_brand_profile ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE identity_provider_credentials ADD COLUMN refresh_expires_at DATETIME(6);
ALTER TABLE identity_provider_credentials ADD COLUMN reauthorization_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE identity_provider_credentials ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
