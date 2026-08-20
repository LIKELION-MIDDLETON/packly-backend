ALTER TABLE analysis_jobs ADD COLUMN photo_object_key VARCHAR(1024);
ALTER TABLE analysis_jobs ADD COLUMN photo_size BIGINT;
ALTER TABLE analysis_jobs ADD COLUMN photo_checksum VARCHAR(64);

ALTER TABLE analysis_jobs ADD CONSTRAINT ck_analysis_photo_size
    CHECK (photo_size IS NULL OR photo_size >= 0);
