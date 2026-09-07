-- P2 social relationship foundation. Relationship history is retained as a state row
-- so a repeated request cannot create duplicate active follows.

CREATE TABLE social_follows (
    follower_user_id BINARY(16) NOT NULL,
    following_user_id BINARY(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    responded_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (follower_user_id, following_user_id),
    KEY ix_social_follows_following_status (following_user_id, status, updated_at DESC),
    KEY ix_social_follows_follower_status (follower_user_id, status, updated_at DESC),
    CONSTRAINT fk_social_follows_follower
        FOREIGN KEY (follower_user_id) REFERENCES core_users (id),
    CONSTRAINT fk_social_follows_following
        FOREIGN KEY (following_user_id) REFERENCES core_users (id),
    CONSTRAINT chk_social_follows_distinct_users
        CHECK (follower_user_id <> following_user_id),
    CONSTRAINT chk_social_follows_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
