ALTER TABLE analysis_jobs ADD COLUMN photo_data BYTEA;
ALTER TABLE analysis_jobs ADD COLUMN photo_content_type VARCHAR(64);

ALTER TABLE recommendation_products ADD COLUMN display_order INTEGER;
ALTER TABLE recommendation_products ADD COLUMN application_order INTEGER;
ALTER TABLE recommendation_products ADD COLUMN usage_group VARCHAR(32);

UPDATE recommendation_products
SET display_order = order_index,
    application_order = CASE
        WHEN order_index BETWEEN 1 AND 3 THEN order_index
        ELSE NULL
    END,
    usage_group = CASE
        WHEN order_index BETWEEN 1 AND 3 THEN 'CORE_ROUTINE'
        WHEN order_index = 4 THEN 'CLEANSE'
        WHEN order_index = 5 THEN 'TREATMENT'
        WHEN order_index = 6 THEN 'PROTECT'
        ELSE 'OCCASIONAL'
    END;

ALTER TABLE recommendation_products ALTER COLUMN display_order SET NOT NULL;
ALTER TABLE recommendation_products ALTER COLUMN usage_group SET NOT NULL;

ALTER TABLE recommendation_products DROP CONSTRAINT ck_product_order;
ALTER TABLE recommendation_products ADD CONSTRAINT ck_product_order CHECK (order_index BETWEEN 1 AND 8);
ALTER TABLE recommendation_products ADD CONSTRAINT ck_product_display_order CHECK (display_order BETWEEN 1 AND 8);
ALTER TABLE recommendation_products ADD CONSTRAINT ck_product_application_order
    CHECK (application_order IS NULL OR application_order BETWEEN 1 AND 3);
ALTER TABLE recommendation_products ADD CONSTRAINT uq_product_recommendation_display_order
    UNIQUE (recommendation_id, display_order);
