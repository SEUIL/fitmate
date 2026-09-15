ALTER TABLE health_inbody_analyses
    DROP CHECK chk_health_inbody_analyses_provider;

UPDATE health_inbody_analyses
SET provider = 'LEGACY'
WHERE provider = CONCAT('GEM', 'INI');

ALTER TABLE health_inbody_analyses
    ADD CONSTRAINT chk_health_inbody_analyses_provider
        CHECK (provider IN ('OPENAI', 'CLAUDE', 'MOCK', 'LEGACY'));
