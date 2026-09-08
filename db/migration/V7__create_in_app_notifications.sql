CREATE TABLE core_notifications (
    id BINARY(16) NOT NULL, user_id BINARY(16) NOT NULL, actor_id BINARY(16) NULL, type VARCHAR(64) NOT NULL,
    event_key VARCHAR(255) NOT NULL, payload_json JSON NOT NULL, read_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_core_notifications_user_event (user_id, event_key),
    KEY ix_core_notifications_user_created (user_id, created_at, id), KEY ix_core_notifications_user_read (user_id, read_at),
    CONSTRAINT fk_core_notifications_user FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_core_notifications_actor FOREIGN KEY (actor_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
