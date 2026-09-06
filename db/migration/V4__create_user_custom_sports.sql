-- Profile-only interests; these names do not extend the managed sport catalog.
CREATE TABLE core_user_custom_sports (
    user_id BINARY(16) NOT NULL,
    name VARCHAR(50) NOT NULL,
    normalized_name VARCHAR(50) COLLATE utf8mb4_0900_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, normalized_name),
    CONSTRAINT fk_core_user_custom_sports_user
        FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT chk_core_user_custom_sports_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_core_user_custom_sports_normalized_name CHECK (CHAR_LENGTH(TRIM(normalized_name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
