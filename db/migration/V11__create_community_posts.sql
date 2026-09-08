CREATE TABLE community_posts (
    id BINARY(16) NOT NULL,
    author_user_id BINARY(16) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_community_posts_feed (deleted_at, created_at DESC, id DESC),
    KEY ix_community_posts_author (author_user_id, deleted_at, created_at DESC),
    CONSTRAINT fk_community_posts_author FOREIGN KEY (author_user_id) REFERENCES core_users (id),
    CONSTRAINT chk_community_posts_visibility CHECK (visibility IN ('PUBLIC', 'MUTUAL')),
    CONSTRAINT chk_community_posts_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_post_media (
    post_id BINARY(16) NOT NULL,
    media_id BINARY(16) NOT NULL,
    display_order SMALLINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (post_id, media_id),
    UNIQUE KEY uk_community_post_media_order (post_id, display_order),
    CONSTRAINT fk_community_post_media_post FOREIGN KEY (post_id) REFERENCES community_posts (id),
    CONSTRAINT fk_community_post_media_asset FOREIGN KEY (media_id) REFERENCES media_assets (id),
    CONSTRAINT chk_community_post_media_order CHECK (display_order < 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_post_likes (
    post_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (post_id, user_id),
    KEY ix_community_post_likes_user (user_id, created_at DESC),
    CONSTRAINT fk_community_post_likes_post FOREIGN KEY (post_id) REFERENCES community_posts (id),
    CONSTRAINT fk_community_post_likes_user FOREIGN KEY (user_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_post_comments (
    id BINARY(16) NOT NULL,
    post_id BINARY(16) NOT NULL,
    author_user_id BINARY(16) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_community_post_comments_post (post_id, deleted_at, created_at ASC),
    CONSTRAINT fk_community_post_comments_post FOREIGN KEY (post_id) REFERENCES community_posts (id),
    CONSTRAINT fk_community_post_comments_author FOREIGN KEY (author_user_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
