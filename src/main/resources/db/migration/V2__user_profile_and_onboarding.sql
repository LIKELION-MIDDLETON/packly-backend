ALTER TABLE users ADD COLUMN nickname VARCHAR(20);
ALTER TABLE users ADD COLUMN normalized_nickname VARCHAR(20);
ALTER TABLE users ADD COLUMN postal_code VARCHAR(5);
ALTER TABLE users ADD COLUMN address_line1 VARCHAR(200);
ALTER TABLE users ADD COLUMN address_line2 VARCHAR(200);

ALTER TABLE users ADD CONSTRAINT uq_users_normalized_nickname UNIQUE (normalized_nickname);

UPDATE users
SET onboarding_status = 'PROFILE_REQUIRED'
WHERE nickname IS NULL OR postal_code IS NULL OR address_line1 IS NULL;
