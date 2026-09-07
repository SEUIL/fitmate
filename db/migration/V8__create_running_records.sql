ALTER TABLE fitness_workout_sessions
    ADD COLUMN sport_code VARCHAR(32) NOT NULL DEFAULT 'HEALTH' AFTER user_id,
    ADD CONSTRAINT chk_fitness_workout_sessions_sport CHECK (sport_code IN ('HEALTH', 'RUNNING'));

CREATE INDEX ix_fitness_workout_sessions_user_sport_started
    ON fitness_workout_sessions (user_id, sport_code, started_at DESC);

CREATE TABLE fitness_running_records (
    workout_session_id BINARY(16) NOT NULL,
    run_type VARCHAR(32) NOT NULL,
    target_distance_meters INT UNSIGNED NULL,
    target_duration_seconds INT UNSIGNED NULL,
    target_pace_sec_per_km INT UNSIGNED NULL,
    interval_seconds INT UNSIGNED NULL,
    route_visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    auto_pause_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    active_duration_seconds INT UNSIGNED NOT NULL,
    total_distance_meters INT UNSIGNED NOT NULL,
    avg_pace_sec_per_km INT UNSIGNED NULL,
    best_pace_sec_per_km INT UNSIGNED NULL,
    avg_speed_kmh DECIMAL(10,2) NULL,
    calories_kcal DECIMAL(8,2) NULL,
    cadence_spm SMALLINT UNSIGNED NULL,
    stride_length_cm DECIMAL(6,2) NULL,
    elevation_gain_meters DECIMAL(8,2) NULL,
    rpe TINYINT UNSIGNED NULL,
    condition_score TINYINT UNSIGNED NULL,
    memo VARCHAR(500) NULL,
    route_json MEDIUMTEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (workout_session_id),
    CONSTRAINT fk_fitness_running_records_session
        FOREIGN KEY (workout_session_id) REFERENCES fitness_workout_sessions (id),
    CONSTRAINT chk_fitness_running_records_type CHECK (
        run_type IN ('FREE', 'DISTANCE_TARGET', 'TIME_TARGET', 'PACE_TARGET', 'INTERVAL')
    ),
    CONSTRAINT chk_fitness_running_records_visibility CHECK (route_visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT chk_fitness_running_records_duration CHECK (active_duration_seconds > 0),
    CONSTRAINT chk_fitness_running_records_distance CHECK (total_distance_meters >= 0),
    CONSTRAINT chk_fitness_running_records_target CHECK (
        (run_type = 'FREE')
        OR (run_type = 'DISTANCE_TARGET' AND target_distance_meters > 0)
        OR (run_type = 'TIME_TARGET' AND target_duration_seconds > 0)
        OR (run_type = 'PACE_TARGET' AND target_distance_meters > 0 AND target_pace_sec_per_km > 0)
        OR (run_type = 'INTERVAL' AND target_duration_seconds > 0 AND interval_seconds > 0)
    ),
    CONSTRAINT chk_fitness_running_records_pace CHECK (
        (total_distance_meters = 0 AND avg_pace_sec_per_km IS NULL AND best_pace_sec_per_km IS NULL)
        OR (total_distance_meters > 0 AND avg_pace_sec_per_km > 0 AND best_pace_sec_per_km > 0)
    ),
    CONSTRAINT chk_fitness_running_records_speed CHECK (
        (total_distance_meters = 0 AND avg_speed_kmh IS NULL)
        OR (total_distance_meters > 0 AND avg_speed_kmh > 0)
    ),
    CONSTRAINT chk_fitness_running_records_rpe CHECK (rpe IS NULL OR rpe BETWEEN 1 AND 10),
    CONSTRAINT chk_fitness_running_records_condition CHECK (
        condition_score IS NULL OR condition_score BETWEEN 1 AND 5
    ),
    CONSTRAINT chk_fitness_running_records_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_running_laps (
    id BINARY(16) NOT NULL,
    workout_session_id BINARY(16) NOT NULL,
    lap_no SMALLINT UNSIGNED NOT NULL,
    started_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NOT NULL,
    distance_meters INT UNSIGNED NOT NULL,
    duration_seconds INT UNSIGNED NOT NULL,
    avg_pace_sec_per_km INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fitness_running_laps_number (workout_session_id, lap_no),
    CONSTRAINT fk_fitness_running_laps_record
        FOREIGN KEY (workout_session_id) REFERENCES fitness_running_records (workout_session_id),
    CONSTRAINT chk_fitness_running_laps_time CHECK (started_at < ended_at),
    CONSTRAINT chk_fitness_running_laps_distance CHECK (distance_meters > 0),
    CONSTRAINT chk_fitness_running_laps_duration CHECK (duration_seconds > 0),
    CONSTRAINT chk_fitness_running_laps_pace CHECK (avg_pace_sec_per_km > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
