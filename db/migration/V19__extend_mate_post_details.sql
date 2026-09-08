ALTER TABLE fitness_mate_posts
    ADD COLUMN meeting_point VARCHAR(500) NULL,
    ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN sport_fields TEXT NULL,
    ADD COLUMN beginner_welcome BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE fitness_mate_posts SET sport_fields = '{}', beginner_welcome = (difficulty = 'BEGINNER');
ALTER TABLE fitness_mate_posts MODIFY COLUMN sport_fields TEXT NOT NULL;
