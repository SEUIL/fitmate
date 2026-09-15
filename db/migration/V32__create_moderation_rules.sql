CREATE TABLE ops_rules (
    id BINARY(16) NOT NULL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    term VARCHAR(100) NOT NULL DEFAULT '',
    threshold_count INT NOT NULL,
    window_seconds INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CHECK (kind IN ('WORD','REPEAT','RATE')),
    CHECK (scope IN ('POST','COMMENT','MATE')),
    CHECK (threshold_count BETWEEN 2 AND 100),
    CHECK (window_seconds BETWEEN 10 AND 3600)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Expired hashes are pruned on that member's next write; no text is copied.
CREATE TABLE ops_rule_events (
    content_id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    KEY ix_ops_rule_events_user_time (user_id, scope, occurred_at),
    CONSTRAINT fk_ops_rule_events_user FOREIGN KEY (user_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ops_rule_revisions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    rule_id BINARY(16) NOT NULL,
    rule_version BIGINT NOT NULL,
    actor_id BINARY(16) NOT NULL,
    configuration_json TEXT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY ux_ops_rule_revision (rule_id, rule_version),
    CONSTRAINT fk_ops_rule_revision_rule FOREIGN KEY (rule_id) REFERENCES ops_rules (id),
    CONSTRAINT fk_ops_rule_revision_actor FOREIGN KEY (actor_id) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ops_rule_detections (
    id BINARY(16) NOT NULL PRIMARY KEY,
    rule_id BINARY(16) NOT NULL,
    rule_version BIGINT NOT NULL,
    rule_name VARCHAR(80) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    content_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    review_reason VARCHAR(300) NULL,
    reviewed_by BINARY(16) NULL,
    reviewed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY ux_ops_rule_detection_once (rule_id, rule_version, content_id),
    KEY ix_ops_rule_detection_created (reviewed, created_at DESC),
    CONSTRAINT fk_ops_rule_detection_rule FOREIGN KEY (rule_id) REFERENCES ops_rules (id),
    CONSTRAINT fk_ops_rule_detection_user FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_ops_rule_detection_reviewer FOREIGN KEY (reviewed_by) REFERENCES core_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
