ALTER TABLE recommendations ADD COLUMN total_price_daily BIGINT;
ALTER TABLE recommendations ALTER COLUMN total_price DROP NOT NULL;
ALTER TABLE recommendations DROP CONSTRAINT ck_recommendation_total;
ALTER TABLE recommendations ADD CONSTRAINT ck_recommendation_total
    CHECK (total_price IS NULL OR total_price >= 0);
ALTER TABLE recommendations ADD CONSTRAINT ck_recommendation_total_daily
    CHECK (total_price_daily IS NULL OR total_price_daily >= 0);

ALTER TABLE recommendation_products ADD COLUMN daily_price BIGINT;
ALTER TABLE recommendation_products ADD COLUMN daily_volume VARCHAR(100);
ALTER TABLE recommendation_products ADD COLUMN total_volume VARCHAR(100);
ALTER TABLE recommendation_products ADD COLUMN sale_price BIGINT;
ALTER TABLE recommendation_products ADD COLUMN recommendation_reason TEXT;
ALTER TABLE recommendation_products ALTER COLUMN price DROP NOT NULL;
ALTER TABLE recommendation_products DROP CONSTRAINT ck_product_price;
ALTER TABLE recommendation_products ADD CONSTRAINT ck_product_price
    CHECK (price IS NULL OR price >= 0);
ALTER TABLE recommendation_products ADD CONSTRAINT ck_product_daily_price
    CHECK (daily_price IS NULL OR daily_price >= 0);
ALTER TABLE recommendation_products ADD CONSTRAINT ck_product_sale_price
    CHECK (sale_price IS NULL OR sale_price >= 0);
