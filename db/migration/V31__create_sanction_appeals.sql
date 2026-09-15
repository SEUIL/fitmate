ALTER TABLE ops_sanctions
    ADD COLUMN revoked_at DATETIME(3) NULL,
    ADD COLUMN revoked_by BINARY(16) NULL,
    ADD COLUMN revocation_reason VARCHAR(500) NULL,
    ADD CONSTRAINT fk_ops_sanctions_revoker FOREIGN KEY (revoked_by) REFERENCES core_users (id);

CREATE TABLE ops_appeal_sessions (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    auth_version BIGINT NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    KEY ix_ops_appeal_sessions_expiry (expires_at),
    CONSTRAINT fk_ops_appeal_sessions_user FOREIGN KEY (user_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ops_appeal_auth_attempts (
    user_id BINARY(16) NOT NULL PRIMARY KEY,
    failures INT NOT NULL DEFAULT 0,
    blocked_until DATETIME(3) NULL,
    CONSTRAINT fk_ops_appeal_attempts_user FOREIGN KEY (user_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ops_appeals (
    id BINARY(16) NOT NULL PRIMARY KEY,
    sanction_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    statement VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    version BIGINT NOT NULL DEFAULT 0,
    public_reason VARCHAR(500) NULL,
    internal_note VARCHAR(500) NULL,
    reviewer_id BINARY(16) NULL,
    reviewed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    open_sanction_id BINARY(16) GENERATED ALWAYS AS (CASE WHEN status IN ('RECEIVED','REVIEWING') THEN sanction_id ELSE NULL END) STORED,
    UNIQUE KEY ux_ops_appeals_open (open_sanction_id),
    KEY ix_ops_appeals_status_created (status, created_at DESC, id DESC),
    KEY ix_ops_appeals_user_created (user_id, created_at DESC),
    CONSTRAINT fk_ops_appeals_sanction FOREIGN KEY (sanction_id) REFERENCES ops_sanctions (id),
    CONSTRAINT fk_ops_appeals_user FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_ops_appeals_reviewer FOREIGN KEY (reviewer_id) REFERENCES core_users (id),
    CONSTRAINT chk_ops_appeals_status CHECK (status IN ('RECEIVED','REVIEWING','ACCEPTED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
