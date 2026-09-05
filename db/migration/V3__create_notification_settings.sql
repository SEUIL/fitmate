-- Backend A P0 notification preferences. Push delivery and device tokens are intentionally excluded.

CREATE TABLE core_notification_settings (
    user_id BINARY(16) NOT NULL,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    dm_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    mate_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    community_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_core_notification_settings_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT chk_core_notification_settings_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
