CREATE TABLE ops_rule_writer_locks (
    user_id BINARY(16) NOT NULL PRIMARY KEY,
    CONSTRAINT fk_ops_rule_writer_user FOREIGN KEY (user_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
