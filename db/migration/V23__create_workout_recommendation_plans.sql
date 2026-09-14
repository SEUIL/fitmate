-- Additive: preserve existing routines and migration history.
CREATE TABLE fitness_workout_plans (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_user_id BINARY(16) NOT NULL,
    request_json LONGTEXT NOT NULL,
    result_json LONGTEXT NULL,
    status VARCHAR(16) NOT NULL,
    generation_token VARCHAR(36) NOT NULL,
    routine_id BINARY(16) NULL,
    message VARCHAR(300) NULL,
    updated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_workout_plan_owner FOREIGN KEY (owner_user_id) REFERENCES core_users(id),
    CONSTRAINT chk_workout_plan_status CHECK (status IN ('GENERATING','READY','FAILED','CANCELED','SAVED')),
    INDEX idx_workout_plan_owner (owner_user_id, created_at)
);
ALTER TABLE fitness_routines ADD COLUMN recommendation_json LONGTEXT NULL;
