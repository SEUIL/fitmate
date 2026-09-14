CREATE TABLE fitness_workout_insights (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_user_id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    request_json LONGTEXT NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    result_json LONGTEXT NULL,
    status VARCHAR(16) NOT NULL,
    generation_token VARCHAR(36) NOT NULL,
    message VARCHAR(300) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_workout_insight_owner FOREIGN KEY (owner_user_id) REFERENCES core_users(id),
    CONSTRAINT fk_workout_insight_session FOREIGN KEY (session_id) REFERENCES fitness_workout_sessions(id),
    CONSTRAINT chk_workout_insight_status CHECK (status IN ('GENERATING','READY','FAILED','CANCELED')),
    INDEX idx_workout_insight_session (owner_user_id, session_id, created_at)
);
