-- Preserve the original provider and results of historical analyses.
-- The Gemini analyzer is no longer available; pending requests cannot resume.
UPDATE health_inbody_analyses
SET status = 'FAILED',
    failure_code = 'PROVIDER_NOT_CONFIGURED',
    failure_message = 'The Gemini provider is no longer available.',
    completed_at = CURRENT_TIMESTAMP(3),
    updated_at = CURRENT_TIMESTAMP(3),
    version = version + 1
WHERE provider = 'GEMINI' AND status IN ('UPLOADED', 'PROCESSING');

ALTER TABLE health_inbody_analyses
    ADD CONSTRAINT chk_health_inbody_gemini_retired CHECK (
        provider <> 'GEMINI' OR status IN ('COMPLETED', 'REVIEW_REQUIRED', 'FAILED')
    );
