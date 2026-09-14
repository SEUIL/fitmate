ALTER TABLE user_reports
    DROP CHECK chk_user_reports_target;

ALTER TABLE user_reports
    ADD CONSTRAINT chk_user_reports_target
        CHECK (target_type IN ('USER', 'POST', 'COMMENT', 'DM_MESSAGE', 'MATE_POST'));
