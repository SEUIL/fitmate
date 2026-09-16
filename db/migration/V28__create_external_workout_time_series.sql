ALTER TABLE health_external_workout_summaries
    ADD COLUMN timeseries_hash CHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN timeseries_version SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    ADD COLUMN timeseries_sample_count INT UNSIGNED NOT NULL DEFAULT 0;

CREATE TABLE health_external_workout_samples (
    id BINARY(16) NOT NULL,
    workout_session_id BINARY(16) NOT NULL,
    measured_at DATETIME(3) NOT NULL,
    heart_rate DECIMAL(6,2) NULL,
    speed_mps DECIMAL(8,3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_health_external_sample_time (workout_session_id, measured_at),
    CONSTRAINT fk_health_external_sample_summary
        FOREIGN KEY (workout_session_id) REFERENCES health_external_workout_summaries (workout_session_id) ON DELETE CASCADE,
    CONSTRAINT chk_health_external_sample_value CHECK (
        heart_rate IS NOT NULL OR speed_mps IS NOT NULL
    ),
    CONSTRAINT chk_health_external_sample_hr CHECK (
        heart_rate IS NULL OR heart_rate > 0 AND heart_rate <= 300
    ),
    CONSTRAINT chk_health_external_sample_speed CHECK (
        speed_mps IS NULL OR speed_mps >= 0 AND speed_mps <= 15
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
