ALTER TABLE fitness_routines
    ADD COLUMN split_count TINYINT UNSIGNED NOT NULL DEFAULT 1 AFTER difficulty,
    ADD CONSTRAINT chk_fitness_routines_split_count CHECK (split_count BETWEEN 1 AND 10);

ALTER TABLE fitness_routine_items
    ADD COLUMN split_day_no TINYINT UNSIGNED NOT NULL DEFAULT 1 AFTER sequence_no,
    ADD CONSTRAINT chk_fitness_routine_items_split_day CHECK (split_day_no BETWEEN 1 AND 10);

ALTER TABLE fitness_workout_sessions
    ADD COLUMN routine_split_day_no TINYINT UNSIGNED NULL AFTER routine_id,
    ADD COLUMN routine_split_count TINYINT UNSIGNED NULL AFTER routine_split_day_no,
    ADD COLUMN routine_workout_day_number INT UNSIGNED NULL AFTER routine_split_count,
    ADD CONSTRAINT chk_fitness_workout_sessions_split_day CHECK (
        routine_split_day_no IS NULL OR routine_split_day_no BETWEEN 1 AND 10
    ),
    ADD CONSTRAINT chk_fitness_workout_sessions_split_count CHECK (
        routine_split_count IS NULL OR routine_split_count BETWEEN 1 AND 10
    ),
    ADD CONSTRAINT chk_fitness_workout_sessions_day_number CHECK (
        routine_workout_day_number IS NULL OR routine_workout_day_number >= 1
    );

UPDATE fitness_routines
SET split_count = 2
WHERE id = UUID_TO_BIN('40000000-0000-0000-0000-000000000002', 1);

UPDATE fitness_routine_items
SET split_day_no = CASE WHEN sequence_no = 3 THEN 2 ELSE 1 END
WHERE routine_id = UUID_TO_BIN('40000000-0000-0000-0000-000000000002', 1);

UPDATE fitness_routines
SET split_count = 3
WHERE id = UUID_TO_BIN('40000000-0000-0000-0000-000000000003', 1);

UPDATE fitness_routine_items
SET split_day_no = CASE
    WHEN sequence_no = 2 THEN 1
    WHEN sequence_no = 3 THEN 2
    ELSE 3
END
WHERE routine_id = UUID_TO_BIN('40000000-0000-0000-0000-000000000003', 1);
