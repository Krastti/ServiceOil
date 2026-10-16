-- Исторические наблюдения сохраняются с исходной периодичностью, без переименования в WEEKLY.
CREATE TABLE regional_price_archive AS
    SELECT * FROM regional_price WHERE frequency <> 'WEEKLY';
DELETE FROM regional_price WHERE frequency <> 'WEEKLY';
ALTER TABLE regional_price ADD CONSTRAINT regional_price_weekly_only CHECK (frequency = 'WEEKLY');
