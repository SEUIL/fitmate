CREATE TABLE social_blocks (
    blocker_user_id BINARY(16) NOT NULL,
    blocked_user_id BINARY(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (blocker_user_id, blocked_user_id),
    KEY ix_social_blocks_blocked (blocked_user_id, created_at DESC),
    CONSTRAINT fk_social_blocks_blocker
        FOREIGN KEY (blocker_user_id) REFERENCES core_users (id),
    CONSTRAINT fk_social_blocks_blocked
        FOREIGN KEY (blocked_user_id) REFERENCES core_users (id),
    CONSTRAINT chk_social_blocks_distinct_users
        CHECK (blocker_user_id <> blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE media_assets (
    id BINARY(16) NOT NULL,
    owner_user_id BINARY(16) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT UNSIGNED NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_assets_storage_key (storage_key),
    KEY ix_media_assets_owner_purpose (owner_user_id, purpose, created_at DESC),
    CONSTRAINT fk_media_assets_owner
        FOREIGN KEY (owner_user_id) REFERENCES core_users (id),
    CONSTRAINT chk_media_assets_purpose
        CHECK (purpose IN ('PROFILE', 'COMMUNITY_POST', 'DM', 'INBODY')),
    CONSTRAINT chk_media_assets_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT chk_media_assets_size CHECK (byte_size > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
