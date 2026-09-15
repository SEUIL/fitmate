-- Operator mutations use one catalog lock; existing roles and UUID storage are preserved.
CREATE TABLE ops_operator_settings (
    id INT NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ops_operator_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_ops_operator_settings_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO ops_operator_settings (id, version) VALUES (1, 0);

CREATE TABLE ops_operator_auth_attempts (
    user_id BINARY(16) NOT NULL PRIMARY KEY,
    failures INT NOT NULL DEFAULT 0,
    blocked_until DATETIME(3) NULL,
    CONSTRAINT fk_ops_operator_auth_attempts_user FOREIGN KEY (user_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ops_operator_reauth (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    auth_version BIGINT NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    KEY ix_ops_operator_reauth_expiry (expires_at),
    CONSTRAINT fk_ops_operator_reauth_user FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_ops_operator_reauth_session FOREIGN KEY (session_id) REFERENCES core_auth_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
