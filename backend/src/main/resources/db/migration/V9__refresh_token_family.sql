-- Tokens issued by one login share a family id. Presenting a revoked token
-- from that family revokes every sibling, so a stolen rotated token cannot
-- keep working after the theft is noticed.

ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
UPDATE refresh_tokens SET family_id = id WHERE family_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
