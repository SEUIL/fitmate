ALTER TABLE fitness_workout_sessions
    DROP CONSTRAINT chk_fitness_workout_sessions_sport,
    ADD CONSTRAINT chk_fitness_workout_sessions_sport
        CHECK (sport_code IN ('HEALTH', 'RUNNING', 'BADMINTON', 'CLIMBING', 'HIKING', 'GOLF'));

CREATE TABLE fitness_sport_records (
    workout_session_id BINARY(16) NOT NULL,
    activity_type VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    active_duration_seconds INT UNSIGNED NOT NULL,
    place_name VARCHAR(100) NULL,
    memo VARCHAR(500) NULL,
    game_count SMALLINT UNSIGNED NULL,
    win_count SMALLINT UNSIGNED NULL,
    attempt_count SMALLINT UNSIGNED NULL,
    completion_count SMALLINT UNSIGNED NULL,
    difficulty_memo VARCHAR(100) NULL,
    course_name VARCHAR(100) NULL,
    distance_meters INT UNSIGNED NULL,
    shot_count SMALLINT UNSIGNED NULL,
    hole_count TINYINT UNSIGNED NULL,
    stroke_count SMALLINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (workout_session_id),
    CONSTRAINT fk_fitness_sport_records_session FOREIGN KEY (workout_session_id) REFERENCES fitness_workout_sessions (id),
    CONSTRAINT chk_fitness_sport_records_activity_type CHECK (
        activity_type IN ('SINGLES', 'DOUBLES', 'PRACTICE', 'BOULDERING', 'LEAD', 'TOP_ROPE', 'HIKE', 'TREKKING', 'SCREEN', 'FIELD')
    ),
    CONSTRAINT chk_fitness_sport_records_duration CHECK (active_duration_seconds > 0),
    CONSTRAINT chk_fitness_sport_records_game CHECK (game_count IS NULL OR game_count <= 9999),
    CONSTRAINT chk_fitness_sport_records_win CHECK (win_count IS NULL OR (win_count <= 9999 AND game_count IS NOT NULL AND win_count <= game_count)),
    CONSTRAINT chk_fitness_sport_records_attempt CHECK (attempt_count IS NULL OR attempt_count <= 9999),
    CONSTRAINT chk_fitness_sport_records_completion CHECK (completion_count IS NULL OR (completion_count <= 9999 AND attempt_count IS NOT NULL AND completion_count <= attempt_count)),
    CONSTRAINT chk_fitness_sport_records_distance CHECK (distance_meters IS NULL OR distance_meters <= 1000000),
    CONSTRAINT chk_fitness_sport_records_shot CHECK (shot_count IS NULL OR shot_count <= 9999),
    CONSTRAINT chk_fitness_sport_records_hole CHECK (hole_count IS NULL OR hole_count BETWEEN 1 AND 18),
    CONSTRAINT chk_fitness_sport_records_stroke CHECK (stroke_count IS NULL OR (stroke_count BETWEEN 1 AND 9999 AND hole_count IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

UPDATE fitness_sports
SET is_active = TRUE, updated_at = UTC_TIMESTAMP(3)
WHERE code IN ('BADMINTON', 'CLIMBING', 'HIKING', 'GOLF');
