-- FitMate P0 schema for MySQL 8.4 (minimum 8.0.16).
-- All timestamps are UTC DATETIME(3). UUID values are stored as BINARY(16).
-- Mate, running, notifications, media, health sync, and erasure jobs are intentionally excluded.

CREATE TABLE core_users (
    id BINARY(16) NOT NULL,
    email VARCHAR(320) NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    active_email_normalized VARCHAR(320)
        GENERATED ALWAYS AS (CASE WHEN account_status <> 'WITHDRAWN' THEN email_normalized ELSE NULL END) STORED,
    password_hash VARCHAR(100) NOT NULL,
    account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    auth_version BIGINT NOT NULL DEFAULT 0,
    last_login_at DATETIME(3) NULL,
    withdrawn_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_core_users_active_email (active_email_normalized),
    CONSTRAINT chk_core_users_status
        CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'WITHDRAWN')),
    CONSTRAINT chk_core_users_auth_version CHECK (auth_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_auth_sessions (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    token_family_id BINARY(16) NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    device_id VARCHAR(128) NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    last_used_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    revoke_reason VARCHAR(32) NULL,
    replaced_by_session_id BINARY(16) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_core_auth_sessions_refresh_hash (refresh_token_hash),
    KEY ix_core_auth_sessions_user_active (user_id, revoked_at, expires_at),
    KEY ix_core_auth_sessions_family_active (token_family_id, revoked_at),
    CONSTRAINT fk_core_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_core_auth_sessions_replaced_by
        FOREIGN KEY (replaced_by_session_id) REFERENCES core_auth_sessions (id),
    CONSTRAINT chk_core_auth_sessions_expiry CHECK (expires_at > issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_revoked_access_tokens (
    jti CHAR(36) NOT NULL,
    user_id BINARY(16) NOT NULL,
    session_id BINARY(16) NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NOT NULL,
    PRIMARY KEY (jti),
    KEY ix_core_revoked_access_tokens_expiry (expires_at),
    CONSTRAINT fk_core_revoked_access_tokens_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_core_revoked_access_tokens_session
        FOREIGN KEY (session_id) REFERENCES core_auth_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_auth_events (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NULL,
    session_id BINARY(16) NULL,
    event_type VARCHAR(32) NOT NULL,
    ip_hash CHAR(64) NULL,
    user_agent_summary VARCHAR(255) NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_core_auth_events_user_occurred (user_id, occurred_at),
    CONSTRAINT fk_core_auth_events_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_core_auth_events_session
        FOREIGN KEY (session_id) REFERENCES core_auth_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_terms_documents (
    id BINARY(16) NOT NULL,
    type VARCHAR(32) NOT NULL,
    version VARCHAR(32) NOT NULL,
    content_uri VARCHAR(512) NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    is_required BOOLEAN NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_core_terms_documents_type_version (type, version),
    CONSTRAINT chk_core_terms_documents_type
        CHECK (type IN ('SERVICE_TERMS', 'PRIVACY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_regions (
    id BINARY(16) NOT NULL,
    parent_id BINARY(16) NULL,
    name VARCHAR(100) NOT NULL,
    level TINYINT UNSIGNED NOT NULL,
    code VARCHAR(32) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_core_regions_code (code),
    UNIQUE KEY uk_core_regions_parent_name (parent_id, name),
    CONSTRAINT fk_core_regions_parent
        FOREIGN KEY (parent_id) REFERENCES core_regions (id),
    CONSTRAINT chk_core_regions_level CHECK (level IN (1, 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_facilities (
    id BINARY(16) NOT NULL,
    name VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,
    region_id BINARY(16) NOT NULL,
    address_summary VARCHAR(255) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_core_facilities_region_name (region_id, normalized_name),
    CONSTRAINT fk_core_facilities_region
        FOREIGN KEY (region_id) REFERENCES core_regions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_user_profiles (
    user_id BINARY(16) NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    nickname_normalized VARCHAR(30) NOT NULL,
    deleted_at DATETIME(3) NULL,
    active_nickname_normalized VARCHAR(30)
        GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN nickname_normalized ELSE NULL END) STORED,
    display_name VARCHAR(50) NOT NULL,
    birth_date DATE NULL,
    gender VARCHAR(16) NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    bio VARCHAR(500) NULL,
    weight_unit VARCHAR(4) NOT NULL DEFAULT 'KG',
    timezone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    onboarding_completed_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_core_user_profiles_active_nickname (active_nickname_normalized),
    CONSTRAINT fk_core_user_profiles_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT chk_core_user_profiles_gender CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'NONE')),
    CONSTRAINT chk_core_user_profiles_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT chk_core_user_profiles_weight_unit CHECK (weight_unit IN ('KG', 'LB')),
    CONSTRAINT chk_core_user_profiles_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_user_consents (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    terms_document_id BINARY(16) NOT NULL,
    consented_at DATETIME(3) NOT NULL,
    withdrawn_at DATETIME(3) NULL,
    evidence JSON NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_core_user_consents_document (user_id, terms_document_id),
    CONSTRAINT fk_core_user_consents_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_core_user_consents_document
        FOREIGN KEY (terms_document_id) REFERENCES core_terms_documents (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_user_activity_regions (
    user_id BINARY(16) NOT NULL,
    region_id BINARY(16) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    primary_user_id BINARY(16)
        GENERATED ALWAYS AS (CASE WHEN is_primary THEN user_id ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, region_id),
    UNIQUE KEY uk_core_user_activity_regions_primary (primary_user_id),
    CONSTRAINT fk_core_user_activity_regions_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_core_user_activity_regions_region
        FOREIGN KEY (region_id) REFERENCES core_regions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_user_facilities (
    user_id BINARY(16) NOT NULL,
    facility_id BINARY(16) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    primary_user_id BINARY(16)
        GENERATED ALWAYS AS (CASE WHEN is_primary THEN user_id ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, facility_id),
    UNIQUE KEY uk_core_user_facilities_primary (primary_user_id),
    CONSTRAINT fk_core_user_facilities_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_core_user_facilities_facility
        FOREIGN KEY (facility_id) REFERENCES core_facilities (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_user_available_times (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    weekday_local TINYINT UNSIGNED NOT NULL,
    start_local_time TIME NOT NULL,
    end_local_time TIME NOT NULL,
    timezone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_core_user_available_times_user (user_id, weekday_local),
    CONSTRAINT fk_core_user_available_times_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT chk_core_user_available_times_weekday CHECK (weekday_local BETWEEN 0 AND 6),
    CONSTRAINT chk_core_user_available_times_range CHECK (start_local_time < end_local_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_sports (
    id BINARY(16) NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(50) NOT NULL,
    display_order SMALLINT UNSIGNED NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fitness_sports_code (code),
    UNIQUE KEY uk_fitness_sports_order (display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_user_sports (
    user_id BINARY(16) NOT NULL,
    sport_id BINARY(16) NOT NULL,
    skill_level VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, sport_id),
    CONSTRAINT fk_core_user_sports_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_core_user_sports_sport
        FOREIGN KEY (sport_id) REFERENCES fitness_sports (id),
    CONSTRAINT chk_core_user_sports_skill_level CHECK (
        skill_level IN ('BEGINNER', 'UNDER_6_MONTHS', 'SIX_MONTHS_TO_ONE_YEAR',
                        'ONE_TO_THREE_YEARS', 'OVER_THREE_YEARS')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE core_user_goals (
    user_id BINARY(16) NOT NULL,
    goal_code VARCHAR(32) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    primary_user_id BINARY(16)
        GENERATED ALWAYS AS (CASE WHEN is_primary THEN user_id ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, goal_code),
    UNIQUE KEY uk_core_user_goals_primary (primary_user_id),
    CONSTRAINT fk_core_user_goals_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT chk_core_user_goals_code CHECK (
        goal_code IN ('DIET', 'MUSCLE_GAIN', 'FITNESS', 'RECORD_IMPROVEMENT', 'HABIT')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_exercise_catalog (
    id BINARY(16) NOT NULL,
    sport_id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    body_part VARCHAR(32) NOT NULL,
    equipment_type VARCHAR(32) NOT NULL,
    reference_url VARCHAR(512) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fitness_exercise_catalog_sport_name (sport_id, normalized_name),
    CONSTRAINT fk_fitness_exercise_catalog_sport
        FOREIGN KEY (sport_id) REFERENCES fitness_sports (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_exercise_substitutions (
    base_exercise_id BINARY(16) NOT NULL,
    substitute_exercise_id BINARY(16) NOT NULL,
    display_order SMALLINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (base_exercise_id, substitute_exercise_id),
    KEY ix_fitness_exercise_substitutions_base_order (base_exercise_id, display_order),
    CONSTRAINT fk_fitness_exercise_substitutions_base
        FOREIGN KEY (base_exercise_id) REFERENCES fitness_exercise_catalog (id),
    CONSTRAINT fk_fitness_exercise_substitutions_substitute
        FOREIGN KEY (substitute_exercise_id) REFERENCES fitness_exercise_catalog (id),
    CONSTRAINT chk_fitness_exercise_substitutions_distinct
        CHECK (base_exercise_id <> substitute_exercise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_routines (
    id BINARY(16) NOT NULL,
    owner_user_id BINARY(16) NULL,
    sport_id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    target_goal_code VARCHAR(32) NULL,
    target_level VARCHAR(32) NULL,
    difficulty VARCHAR(32) NOT NULL,
    is_template BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_fitness_routines_template_match (sport_id, is_template, is_active, target_goal_code, target_level),
    CONSTRAINT fk_fitness_routines_owner
        FOREIGN KEY (owner_user_id) REFERENCES core_users (id),
    CONSTRAINT fk_fitness_routines_sport
        FOREIGN KEY (sport_id) REFERENCES fitness_sports (id),
    CONSTRAINT chk_fitness_routines_source CHECK (
        source_type IN ('SYSTEM_TEMPLATE', 'USER_COPY', 'MANUAL')
    ),
    CONSTRAINT chk_fitness_routines_goal CHECK (
        target_goal_code IS NULL OR target_goal_code IN ('DIET', 'MUSCLE_GAIN', 'FITNESS', 'RECORD_IMPROVEMENT', 'HABIT')
    ),
    CONSTRAINT chk_fitness_routines_level CHECK (
        target_level IS NULL OR target_level IN ('BEGINNER', 'UNDER_6_MONTHS', 'SIX_MONTHS_TO_ONE_YEAR',
                                                  'ONE_TO_THREE_YEARS', 'OVER_THREE_YEARS')
    ),
    CONSTRAINT chk_fitness_routines_owner CHECK (
        (is_template = TRUE AND owner_user_id IS NULL) OR
        (is_template = FALSE AND owner_user_id IS NOT NULL)
    ),
    CONSTRAINT chk_fitness_routines_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_routine_items (
    id BINARY(16) NOT NULL,
    routine_id BINARY(16) NOT NULL,
    exercise_id BINARY(16) NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    target_sets SMALLINT UNSIGNED NULL,
    target_reps_min SMALLINT UNSIGNED NULL,
    target_reps_max SMALLINT UNSIGNED NULL,
    target_weight_kg DECIMAL(7,2) NULL,
    target_rpe TINYINT UNSIGNED NULL,
    rest_seconds SMALLINT UNSIGNED NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fitness_routine_items_sequence (routine_id, sequence_no),
    CONSTRAINT fk_fitness_routine_items_routine
        FOREIGN KEY (routine_id) REFERENCES fitness_routines (id),
    CONSTRAINT fk_fitness_routine_items_exercise
        FOREIGN KEY (exercise_id) REFERENCES fitness_exercise_catalog (id),
    CONSTRAINT chk_fitness_routine_items_reps CHECK (
        target_reps_min IS NULL OR target_reps_max IS NULL OR target_reps_min <= target_reps_max
    ),
    CONSTRAINT chk_fitness_routine_items_weight CHECK (target_weight_kg IS NULL OR target_weight_kg >= 0),
    CONSTRAINT chk_fitness_routine_items_rpe CHECK (target_rpe IS NULL OR target_rpe BETWEEN 1 AND 10),
    CONSTRAINT chk_fitness_routine_items_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_workout_sessions (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    routine_id BINARY(16) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'READY',
    started_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NULL,
    cancel_reason VARCHAR(255) NULL,
    client_updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_fitness_workout_sessions_user_started (user_id, started_at DESC, id DESC),
    CONSTRAINT fk_fitness_workout_sessions_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_fitness_workout_sessions_routine
        FOREIGN KEY (routine_id) REFERENCES fitness_routines (id),
    CONSTRAINT chk_fitness_workout_sessions_status CHECK (
        status IN ('READY', 'IN_PROGRESS', 'PAUSED', 'SYNC_WAITING', 'REVIEW_REQUIRED', 'COMPLETED', 'CANCELED')
    ),
    CONSTRAINT chk_fitness_workout_sessions_time CHECK (ended_at IS NULL OR started_at < ended_at),
    CONSTRAINT chk_fitness_workout_sessions_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_workout_session_exercises (
    id BINARY(16) NOT NULL,
    workout_session_id BINARY(16) NOT NULL,
    exercise_id BINARY(16) NULL,
    display_name_snapshot VARCHAR(100) NOT NULL,
    body_part_snapshot VARCHAR(32) NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    completed_at DATETIME(3) NULL,
    client_updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fitness_session_exercises_sequence (workout_session_id, sequence_no),
    CONSTRAINT fk_fitness_session_exercises_session
        FOREIGN KEY (workout_session_id) REFERENCES fitness_workout_sessions (id),
    CONSTRAINT fk_fitness_session_exercises_catalog
        FOREIGN KEY (exercise_id) REFERENCES fitness_exercise_catalog (id),
    CONSTRAINT chk_fitness_session_exercises_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_workout_sets (
    id BINARY(16) NOT NULL,
    session_exercise_id BINARY(16) NOT NULL,
    set_no SMALLINT UNSIGNED NOT NULL,
    weight_kg DECIMAL(7,2) NULL,
    repetitions SMALLINT UNSIGNED NULL,
    duration_seconds INT UNSIGNED NULL,
    rest_seconds INT UNSIGNED NULL,
    rpe TINYINT UNSIGNED NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    memo VARCHAR(500) NULL,
    client_updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fitness_workout_sets_number (session_exercise_id, set_no),
    CONSTRAINT fk_fitness_workout_sets_session_exercise
        FOREIGN KEY (session_exercise_id) REFERENCES fitness_workout_session_exercises (id),
    CONSTRAINT chk_fitness_workout_sets_weight CHECK (weight_kg IS NULL OR weight_kg >= 0),
    CONSTRAINT chk_fitness_workout_sets_rpe CHECK (rpe IS NULL OR rpe BETWEEN 1 AND 10),
    CONSTRAINT chk_fitness_workout_sets_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ops_idempotency_keys (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    idempotency_key BINARY(16) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status SMALLINT UNSIGNED NOT NULL,
    response_body JSON NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_idempotency_keys_user_key (user_id, idempotency_key),
    KEY ix_ops_idempotency_keys_expiry (expires_at),
    CONSTRAINT fk_ops_idempotency_keys_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT chk_ops_idempotency_keys_status CHECK (response_status BETWEEN 100 AND 599)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
