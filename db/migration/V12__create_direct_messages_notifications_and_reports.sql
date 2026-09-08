CREATE TABLE dm_conversations (
    id BINARY(16) NOT NULL,
    first_user_id BINARY(16) NOT NULL,
    second_user_id BINARY(16) NOT NULL,
    requester_user_id BINARY(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    responded_at DATETIME(3) NULL,
    last_message_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dm_conversations_pair (first_user_id, second_user_id),
    KEY ix_dm_conversations_first (first_user_id, last_message_at DESC),
    KEY ix_dm_conversations_second (second_user_id, last_message_at DESC),
    CONSTRAINT fk_dm_conversations_first FOREIGN KEY (first_user_id) REFERENCES core_users (id),
    CONSTRAINT fk_dm_conversations_second FOREIGN KEY (second_user_id) REFERENCES core_users (id),
    CONSTRAINT fk_dm_conversations_requester FOREIGN KEY (requester_user_id) REFERENCES core_users (id),
    CONSTRAINT chk_dm_conversations_distinct CHECK (first_user_id <> second_user_id),
    CONSTRAINT chk_dm_conversations_status CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dm_messages (
    id BINARY(16) NOT NULL,
    conversation_id BINARY(16) NOT NULL,
    sender_user_id BINARY(16) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    media_id BINARY(16) NULL,
    read_at DATETIME(3) NULL,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_dm_messages_conversation (conversation_id, created_at DESC),
    CONSTRAINT fk_dm_messages_conversation FOREIGN KEY (conversation_id) REFERENCES dm_conversations (id),
    CONSTRAINT fk_dm_messages_sender FOREIGN KEY (sender_user_id) REFERENCES core_users (id),
    CONSTRAINT fk_dm_messages_media FOREIGN KEY (media_id) REFERENCES media_assets (id),
    CONSTRAINT chk_dm_messages_content_or_media CHECK (char_length(content) > 0 OR media_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_notifications (
    id BINARY(16) NOT NULL,
    recipient_user_id BINARY(16) NOT NULL,
    actor_user_id BINARY(16) NULL,
    type VARCHAR(32) NOT NULL,
    resource_type VARCHAR(32) NULL,
    resource_id BINARY(16) NULL,
    body VARCHAR(500) NULL,
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_user_notifications_recipient (recipient_user_id, read_at, created_at DESC),
    CONSTRAINT fk_user_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES core_users (id),
    CONSTRAINT fk_user_notifications_actor FOREIGN KEY (actor_user_id) REFERENCES core_users (id),
    CONSTRAINT chk_user_notifications_type CHECK (type IN ('FOLLOW_REQUEST', 'FOLLOW_ACCEPTED', 'POST_LIKED', 'POST_COMMENTED', 'DM_REQUEST', 'DM_MESSAGE', 'REPORT_STATUS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_reports (
    id BINARY(16) NOT NULL,
    reporter_user_id BINARY(16) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BINARY(16) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    detail VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    resolved_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_reports_once (reporter_user_id, target_type, target_id),
    KEY ix_user_reports_status (status, created_at DESC),
    CONSTRAINT fk_user_reports_reporter FOREIGN KEY (reporter_user_id) REFERENCES core_users (id),
    CONSTRAINT chk_user_reports_target CHECK (target_type IN ('USER', 'POST', 'COMMENT', 'DM_MESSAGE')),
    CONSTRAINT chk_user_reports_reason CHECK (reason IN ('SPAM', 'HARASSMENT', 'HATE', 'SEXUAL', 'VIOLENCE', 'SELF_HARM', 'OTHER')),
    CONSTRAINT chk_user_reports_status CHECK (status IN ('RECEIVED', 'REVIEWING', 'RESOLVED', 'DISMISSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
