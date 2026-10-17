-- Блокировка теперь транзакционная advisory; отдельная постоянная строка не нужна.
DROP TABLE scheduled_import_lock;

ALTER TABLE region ADD CONSTRAINT ck_region_text CHECK (length(trim(code)) > 0 AND length(trim(name)) > 0);
ALTER TABLE fuel_type ADD CONSTRAINT ck_fuel_text CHECK (length(trim(code)) > 0 AND length(trim(name)) > 0);
ALTER TABLE data_source ADD CONSTRAINT ck_source_text CHECK (
    length(trim(code)) > 0 AND length(trim(name)) > 0 AND length(trim(methodology)) > 0);
ALTER TABLE regional_price ADD CONSTRAINT ck_price_url CHECK (length(trim(source_url)) > 0);

ALTER TABLE import_run ADD CONSTRAINT ck_import_source CHECK (length(trim(source_code)) > 0);
ALTER TABLE import_run ADD CONSTRAINT ck_import_time CHECK (finished_at >= started_at);
ALTER TABLE import_run ADD CONSTRAINT ck_import_result CHECK (
    (status = 'SUCCESS' AND rejected_count = 0 AND error_code IS NULL)
    OR (status = 'FAILED' AND row_count = 0 AND rejected_count > 0
        AND error_code IS NOT NULL AND length(trim(error_code)) > 0));
CREATE INDEX ix_import_source_latest ON import_run (source_code, id DESC);

-- CREATE TABLE AS в V4 не переносит NOT NULL, ключи и CHECK исходной таблицы.
ALTER TABLE regional_price_archive ALTER COLUMN source_code SET NOT NULL;
ALTER TABLE regional_price_archive ALTER COLUMN region_code SET NOT NULL;
ALTER TABLE regional_price_archive ALTER COLUMN fuel_code SET NOT NULL;
ALTER TABLE regional_price_archive ALTER COLUMN observed_on SET NOT NULL;
ALTER TABLE regional_price_archive ALTER COLUMN frequency SET NOT NULL;
ALTER TABLE regional_price_archive ALTER COLUMN price SET NOT NULL;
ALTER TABLE regional_price_archive ALTER COLUMN currency SET NOT NULL;
ALTER TABLE regional_price_archive ALTER COLUMN unit SET NOT NULL;
ALTER TABLE regional_price_archive ALTER COLUMN source_url SET NOT NULL;
ALTER TABLE regional_price_archive ALTER COLUMN imported_at SET NOT NULL;
ALTER TABLE regional_price_archive ADD CONSTRAINT ck_archive_text CHECK (
    length(trim(source_code)) > 0 AND length(trim(region_code)) > 0
    AND length(trim(fuel_code)) > 0 AND length(trim(source_url)) > 0);
ALTER TABLE regional_price_archive ADD CONSTRAINT ck_archive_price CHECK (price > 0);
ALTER TABLE regional_price_archive ADD CONSTRAINT ck_archive_currency CHECK (currency = 'RUB');
ALTER TABLE regional_price_archive ADD CONSTRAINT ck_archive_unit CHECK (unit = 'LITER');
ALTER TABLE regional_price_archive ADD CONSTRAINT ck_archive_frequency CHECK (frequency IN ('DAILY', 'MONTHLY'));
