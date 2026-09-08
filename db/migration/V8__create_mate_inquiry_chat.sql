CREATE TABLE fitness_mate_chat_rooms (
    id BINARY(16) NOT NULL, mate_post_id BINARY(16) NOT NULL, inquirer_id BINARY(16) NOT NULL,
    last_message_sequence BIGINT NOT NULL DEFAULT 0, host_last_read_sequence BIGINT NOT NULL DEFAULT 0, inquirer_last_read_sequence BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_fitness_mate_chat_rooms_post_inquirer (mate_post_id, inquirer_id),
    KEY ix_fitness_mate_chat_rooms_inquirer (inquirer_id, updated_at),
    CONSTRAINT fk_fitness_mate_chat_rooms_post FOREIGN KEY (mate_post_id) REFERENCES fitness_mate_posts (id),
    CONSTRAINT fk_fitness_mate_chat_rooms_inquirer FOREIGN KEY (inquirer_id) REFERENCES core_users (id),
    CONSTRAINT chk_fitness_mate_chat_rooms_sequences CHECK (last_message_sequence >= 0 AND host_last_read_sequence >= 0 AND inquirer_last_read_sequence >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fitness_mate_chat_messages (
    id BINARY(16) NOT NULL, room_id BINARY(16) NOT NULL, sender_id BINARY(16) NOT NULL, client_message_id BINARY(16) NOT NULL,
    sequence_no BIGINT NOT NULL, body VARCHAR(2000) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_fitness_mate_chat_messages_client (room_id, sender_id, client_message_id),
    UNIQUE KEY uk_fitness_mate_chat_messages_sequence (room_id, sequence_no), KEY ix_fitness_mate_chat_messages_room_sequence (room_id, sequence_no),
    CONSTRAINT fk_fitness_mate_chat_messages_room FOREIGN KEY (room_id) REFERENCES fitness_mate_chat_rooms (id),
    CONSTRAINT fk_fitness_mate_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
