ALTER TABLE campaign_campaign ADD COLUMN change_sequence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE campaign_application ADD COLUMN decided_at DATETIME(6);
ALTER TABLE campaign_application ADD COLUMN decided_by CHAR(36);
ALTER TABLE campaign_application ADD COLUMN decision_reason VARCHAR(2000);
CREATE INDEX idx_application_campaign_status ON campaign_application(campaign_id,status,submitted_at);
-- Correct only the known legacy demo records, which stored account IDs instead of creator profile IDs.
UPDATE campaign_application AS target SET creator_id='20000000-0000-4000-8000-000000000002'
WHERE application_id IN ('30000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000002')
AND creator_id='20000000-0000-4000-8000-000000000001'
AND NOT EXISTS (SELECT 1 FROM (SELECT campaign_id,creator_id FROM campaign_application GROUP BY campaign_id,creator_id) AS existing
WHERE existing.campaign_id=target.campaign_id AND existing.creator_id='20000000-0000-4000-8000-000000000002');
UPDATE campaign_application AS target SET creator_id='20000000-0000-4000-8000-000000000004'
WHERE application_id='30000000-0000-4000-8000-000000000003' AND creator_id='20000000-0000-4000-8000-000000000003'
AND NOT EXISTS (SELECT 1 FROM (SELECT campaign_id,creator_id FROM campaign_application GROUP BY campaign_id,creator_id) AS existing
WHERE existing.campaign_id=target.campaign_id AND existing.creator_id='20000000-0000-4000-8000-000000000004');
UPDATE campaign_campaign SET brand_id='10000000-0000-4000-8000-000000000002'
WHERE campaign_id IN ('40000000-0000-4000-8000-000000000001','40000000-0000-4000-8000-000000000002',
'40000000-0000-4000-8000-000000000003','40000000-0000-4000-8000-000000000004','40000000-0000-4000-8000-000000000005')
AND brand_id='10000000-0000-4000-8000-000000000001';
