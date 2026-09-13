CREATE TABLE health_inbody_analyses (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    media_id BINARY(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_model VARCHAR(100) NULL,
    result_json MEDIUMTEXT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    completed_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_health_inbody_analyses_user_created (user_id, created_at DESC),
    CONSTRAINT fk_health_inbody_analyses_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_health_inbody_analyses_media
        FOREIGN KEY (media_id) REFERENCES media_assets (id),
    CONSTRAINT chk_health_inbody_analyses_status CHECK (
        status IN ('UPLOADED', 'PROCESSING', 'COMPLETED', 'REVIEW_REQUIRED', 'FAILED')
    ),
    CONSTRAINT chk_health_inbody_analyses_provider CHECK (
        provider IN ('OPENAI', 'CLAUDE', 'MOCK')
    ),
    CONSTRAINT chk_health_inbody_analyses_attempt CHECK (attempt_count >= 0),
    CONSTRAINT chk_health_inbody_analyses_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE health_inbody_records (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    analysis_id BINARY(16) NULL,
    source_type VARCHAR(32) NOT NULL,
    measured_date DATE NOT NULL,
    weight_kg DECIMAL(6,2) NOT NULL,
    skeletal_muscle_mass_kg DECIMAL(6,2) NOT NULL,
    body_fat_percentage DECIMAL(5,2) NOT NULL,
    optional_values_json MEDIUMTEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_health_inbody_records_analysis (analysis_id),
    KEY ix_health_inbody_records_user_measured (user_id, measured_date DESC, id),
    CONSTRAINT fk_health_inbody_records_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_health_inbody_records_analysis
        FOREIGN KEY (analysis_id) REFERENCES health_inbody_analyses (id),
    CONSTRAINT chk_health_inbody_records_source CHECK (source_type IN ('MANUAL', 'AI_OCR')),
    CONSTRAINT chk_health_inbody_records_weight CHECK (weight_kg BETWEEN 1 AND 500),
    CONSTRAINT chk_health_inbody_records_muscle CHECK (
        skeletal_muscle_mass_kg >= 0 AND skeletal_muscle_mass_kg <= weight_kg
    ),
    CONSTRAINT chk_health_inbody_records_fat CHECK (body_fat_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_health_inbody_records_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
