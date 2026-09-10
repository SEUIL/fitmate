CREATE TABLE health_external_workout_summaries (
    workout_session_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    provider VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source_record_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source_app_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    source_app_key VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (COALESCE(source_app_id, '')) STORED,
    source_modified_at DATETIME(3) NULL,
    exercise_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    started_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NOT NULL,
    active_duration_seconds BIGINT UNSIGNED NOT NULL,
    distance_meters DECIMAL(12,2) NULL,
    calories_kcal DECIMAL(12,2) NULL,
    mean_heart_rate DECIMAL(6,2) NULL,
    min_heart_rate DECIMAL(6,2) NULL,
    max_heart_rate DECIMAL(6,2) NULL,
    payload_hash CHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    saved_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (workout_session_id),
    UNIQUE KEY uk_health_external_source (user_id, provider, source_app_key, source_record_id),
    CONSTRAINT fk_health_external_session FOREIGN KEY (workout_session_id) REFERENCES fitness_workout_sessions (id),
    CONSTRAINT fk_health_external_user FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT chk_health_external_provider CHECK (provider = 'SAMSUNG_HEALTH_DATA_SDK'),
    CONSTRAINT chk_health_external_time CHECK (started_at < ended_at),
    CONSTRAINT chk_health_external_active CHECK (active_duration_seconds > 0),
    CONSTRAINT chk_health_external_distance CHECK (distance_meters IS NULL OR distance_meters >= 0),
    CONSTRAINT chk_health_external_calories CHECK (calories_kcal IS NULL OR calories_kcal >= 0),
    CONSTRAINT chk_health_external_heartrate CHECK (
        (min_heart_rate IS NULL OR min_heart_rate BETWEEN 0 AND 300) AND
        (mean_heart_rate IS NULL OR mean_heart_rate BETWEEN 0 AND 300) AND
        (max_heart_rate IS NULL OR max_heart_rate BETWEEN 0 AND 300) AND
        (min_heart_rate IS NULL OR max_heart_rate IS NULL OR min_heart_rate <= max_heart_rate) AND
        (mean_heart_rate IS NULL OR min_heart_rate IS NULL OR mean_heart_rate >= min_heart_rate) AND
        (mean_heart_rate IS NULL OR max_heart_rate IS NULL OR mean_heart_rate <= max_heart_rate)
    ),
    CONSTRAINT chk_health_external_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
