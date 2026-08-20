CREATE TABLE users (
    id UUID PRIMARY KEY,
    normalized_email VARCHAR(320) NOT NULL,
    email VARCHAR(320) NOT NULL,
    name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(2000),
    onboarding_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE oauth_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320) NOT NULL,
    email_verified BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_oauth_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_oauth_identity_user ON oauth_identities(user_id);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    session_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_refresh_family ON refresh_sessions(family_id);
CREATE INDEX idx_refresh_user ON refresh_sessions(user_id);

CREATE TABLE surveys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    skin_type INTEGER NOT NULL,
    concerns VARCHAR(255) NOT NULL,
    duration INTEGER NOT NULL,
    areas VARCHAR(255) NOT NULL,
    irritation INTEGER NOT NULL,
    diagnosed INTEGER NOT NULL,
    diagnosed_text VARCHAR(500),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_survey_skin_type CHECK (skin_type BETWEEN 1 AND 6),
    CONSTRAINT ck_survey_duration CHECK (duration BETWEEN 1 AND 5),
    CONSTRAINT ck_survey_irritation CHECK (irritation BETWEEN 1 AND 3),
    CONSTRAINT ck_survey_diagnosed CHECK (diagnosed BETWEEN 1 AND 6)
);

CREATE TABLE analysis_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    survey_id UUID NOT NULL REFERENCES surveys(id),
    survey_snapshot TEXT NOT NULL,
    budget_total BIGINT,
    status VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    external_job_id VARCHAR(255),
    source_result_id VARCHAR(255) UNIQUE,
    failure_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_analysis_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_analysis_budget CHECK (budget_total IS NULL OR budget_total >= 0)
);

CREATE INDEX idx_analysis_user_created ON analysis_jobs(user_id, created_at);

CREATE TABLE recommendations (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL UNIQUE REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    diagnosis VARCHAR(100) NOT NULL,
    headline VARCHAR(500) NOT NULL,
    summary TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    triage VARCHAR(64) NOT NULL,
    medical_recommended BOOLEAN NOT NULL,
    medical_reasons TEXT NOT NULL,
    reflected_survey TEXT,
    total_price BIGINT NOT NULL,
    analysis_summary TEXT,
    care_recommendations TEXT NOT NULL,
    disclaimer TEXT,
    ai_request_snapshot TEXT NOT NULL,
    ai_response_snapshot TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_recommendation_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT ck_recommendation_total CHECK (total_price >= 0)
);

CREATE INDEX idx_recommendation_user_created ON recommendations(user_id, created_at, id);

CREATE TABLE recommendation_products (
    id UUID PRIMARY KEY,
    recommendation_id UUID NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
    order_index INTEGER NOT NULL,
    slot VARCHAR(100) NOT NULL,
    goods_no VARCHAR(100) NOT NULL,
    brand VARCHAR(255) NOT NULL,
    name VARCHAR(500) NOT NULL,
    price BIGINT NOT NULL,
    suitability TEXT,
    suitability_source VARCHAR(32),
    functional_info TEXT,
    unscented BOOLEAN NOT NULL,
    comedogenic_score INTEGER NOT NULL,
    CONSTRAINT uq_product_recommendation_order UNIQUE (recommendation_id, order_index),
    CONSTRAINT ck_product_order CHECK (order_index BETWEEN 1 AND 3),
    CONSTRAINT ck_product_price CHECK (price >= 0)
);

CREATE TABLE product_usage_completions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recommendation_product_id UUID NOT NULL REFERENCES recommendation_products(id) ON DELETE CASCADE,
    used_on DATE NOT NULL,
    completed BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_completion_user_product_date UNIQUE (user_id, recommendation_product_id, used_on)
);

CREATE TABLE recommendation_feedback (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recommendation_id UUID NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL,
    comment VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_feedback_user_recommendation UNIQUE (user_id, recommendation_id),
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE sos_reports (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recommendation_id UUID REFERENCES recommendations(id) ON DELETE SET NULL,
    message VARCHAR(2000) NOT NULL,
    symptom_labels TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_sos_user_created ON sos_reports(user_id, created_at);
