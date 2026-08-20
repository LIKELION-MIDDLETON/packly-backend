ALTER TABLE users ADD CONSTRAINT ck_users_profile_required_fields CHECK (
    onboarding_status = 'PROFILE_REQUIRED'
    OR (nickname IS NOT NULL AND normalized_nickname IS NOT NULL
        AND postal_code IS NOT NULL AND address_line1 IS NOT NULL)
);
