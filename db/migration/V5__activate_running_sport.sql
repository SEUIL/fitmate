-- HEALTH and RUNNING are the two base solo-workout entry modes.
-- Running record persistence remains a separate follow-up feature.
UPDATE fitness_sports
SET is_active = TRUE,
    updated_at = UTC_TIMESTAMP(3)
WHERE code = 'RUNNING';
