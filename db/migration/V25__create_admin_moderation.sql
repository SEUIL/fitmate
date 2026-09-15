CREATE TABLE ops_admin_roles (
    user_id BINARY(16) NOT NULL,
    role VARCHAR(16) NOT NULL,
    granted_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_ops_admin_roles_user FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT chk_ops_admin_roles_role CHECK (role IN ('MODERATOR', 'ADMIN', 'SUPER_ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE user_reports
    ADD COLUMN assigned_operator_id BINARY(16) NULL,
    ADD COLUMN decision_action VARCHAR(16) NULL,
    ADD COLUMN decision_reason_code VARCHAR(32) NULL,
    ADD COLUMN public_reason VARCHAR(500) NULL,
    ADD COLUMN internal_note VARCHAR(500) NULL,
    ADD COLUMN subject_user_id BINARY(16) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_user_reports_assigned_operator FOREIGN KEY (assigned_operator_id) REFERENCES core_users (id),
    ADD CONSTRAINT fk_user_reports_subject_user FOREIGN KEY (subject_user_id) REFERENCES core_users (id),
    ADD CONSTRAINT chk_user_reports_version CHECK (version >= 0);

UPDATE user_reports
SET subject_user_id = target_id
WHERE target_type = 'USER';

CREATE TABLE ops_sanctions (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    report_id BINARY(16) NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    public_reason VARCHAR(500) NOT NULL,
    starts_at DATETIME(3) NOT NULL,
    ends_at DATETIME(3) NULL,
    created_by BINARY(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_ops_sanctions_user_active (user_id, ends_at),
    CONSTRAINT fk_ops_sanctions_user FOREIGN KEY (user_id) REFERENCES core_users (id),
    CONSTRAINT fk_ops_sanctions_report FOREIGN KEY (report_id) REFERENCES user_reports (id),
    CONSTRAINT fk_ops_sanctions_admin FOREIGN KEY (created_by) REFERENCES core_users (id),
    CONSTRAINT chk_ops_sanctions_action CHECK (action IN ('WARNING', 'SUSPEND')),
    CONSTRAINT chk_ops_sanctions_time CHECK (ends_at IS NULL OR ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ops_audit_logs (
    id BINARY(16) NOT NULL,
    actor_user_id BINARY(16) NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BINARY(16) NOT NULL,
    report_id BINARY(16) NULL,
    summary VARCHAR(500) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_ops_audit_logs_report (report_id, occurred_at DESC),
    CONSTRAINT fk_ops_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES core_users (id),
    CONSTRAINT fk_ops_audit_logs_report FOREIGN KEY (report_id) REFERENCES user_reports (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
