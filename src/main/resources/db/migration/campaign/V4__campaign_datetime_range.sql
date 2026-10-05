-- V3 has already been applied in verification databases. Preserve it and extend user-entered date range.
ALTER TABLE campaign_campaign MODIFY COLUMN publication_date DATETIME(6);
ALTER TABLE campaign_campaign MODIFY COLUMN application_deadline DATETIME(6);
ALTER TABLE campaign_deliverable_specification MODIFY COLUMN deadline DATETIME(6) NOT NULL;
