CREATE TABLE fitness_mate_posts (
    id BINARY(16) NOT NULL, author_id BINARY(16) NOT NULL, sport_id BINARY(16) NOT NULL, region_id BINARY(16) NOT NULL,
    title VARCHAR(100) NOT NULL, body TEXT NOT NULL, scheduled_start_at DATETIME(3) NOT NULL, duration_seconds INT UNSIGNED NULL,
    location_summary VARCHAR(255) NOT NULL, capacity SMALLINT UNSIGNED NOT NULL DEFAULT 2, accepted_count SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    difficulty VARCHAR(16) NOT NULL, estimated_cost_krw INT UNSIGNED NULL, status VARCHAR(16) NOT NULL DEFAULT 'RECRUITING',
    version BIGINT NOT NULL DEFAULT 0, closed_at DATETIME(3) NULL, canceled_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id), KEY ix_fitness_mate_posts_list (status, scheduled_start_at, id), KEY ix_fitness_mate_posts_author (author_id, created_at),
    CONSTRAINT fk_fitness_mate_posts_author FOREIGN KEY (author_id) REFERENCES core_users (id),
    CONSTRAINT fk_fitness_mate_posts_sport FOREIGN KEY (sport_id) REFERENCES fitness_sports (id),
    CONSTRAINT fk_fitness_mate_posts_region FOREIGN KEY (region_id) REFERENCES core_regions (id),
    CONSTRAINT chk_fitness_mate_posts_capacity CHECK (capacity BETWEEN 2 AND 100 AND accepted_count BETWEEN 0 AND capacity),
    CONSTRAINT chk_fitness_mate_posts_status CHECK (status IN ('RECRUITING', 'FULL', 'CLOSED', 'CANCELED')),
    CONSTRAINT chk_fitness_mate_posts_difficulty CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT chk_fitness_mate_posts_duration CHECK (duration_seconds IS NULL OR duration_seconds > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_mate_participations (
    id BINARY(16) NOT NULL, mate_post_id BINARY(16) NOT NULL, user_id BINARY(16) NOT NULL,
    participant_role VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL, status_reason VARCHAR(32) NULL,
    applied_at DATETIME(3) NOT NULL, responded_at DATETIME(3) NULL, canceled_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_fitness_mate_participations_post_user (mate_post_id, user_id),
    KEY ix_fitness_mate_participations_post_status (mate_post_id, status), KEY ix_fitness_mate_participations_user (user_id, created_at),
    CONSTRAINT fk_fitness_mate_participations_post FOREIGN KEY (mate_post_id) REFERENCES fitness_mate_posts (id),
    CONSTRAINT fk_fitness_mate_participations_user FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT chk_fitness_mate_participations_role CHECK (participant_role IN ('HOST', 'MEMBER')),
    CONSTRAINT chk_fitness_mate_participations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED', 'WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
