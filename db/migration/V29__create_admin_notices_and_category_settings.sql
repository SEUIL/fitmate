-- V28 is reserved until the laptop-only migration mentioned in the handoff is identified.
CREATE TABLE ops_notices (
    id BINARY(16) NOT NULL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    published_at DATETIME(3) NULL,
    ended_at DATETIME(3) NULL,
    created_by BINARY(16) NOT NULL,
    updated_by BINARY(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ops_notices_creator FOREIGN KEY (created_by) REFERENCES core_users(id),
    CONSTRAINT fk_ops_notices_editor FOREIGN KEY (updated_by) REFERENCES core_users(id),
    CONSTRAINT chk_ops_notices_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ENDED')),
    CONSTRAINT chk_ops_notices_version CHECK (version >= 0),
    CONSTRAINT chk_ops_notices_content CHECK (CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 100 AND CHAR_LENGTH(TRIM(body)) BETWEEN 1 AND 5000),
    CONSTRAINT chk_ops_notices_lifecycle CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND ended_at IS NULL) OR
        (status = 'PUBLISHED' AND published_at IS NOT NULL AND ended_at IS NULL) OR
        (status = 'ENDED' AND published_at IS NOT NULL AND ended_at IS NOT NULL)
    ),
    KEY ix_ops_notices_public (status, published_at DESC, id DESC),
    KEY ix_ops_notices_updated (updated_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ops_category_settings (
    id TINYINT NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ops_category_settings_id CHECK (id = 1),
    CONSTRAINT chk_ops_category_settings_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO ops_category_settings(id, version) VALUES (1, 0);
