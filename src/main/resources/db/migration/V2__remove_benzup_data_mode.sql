-- ADD CONSTRAINT проверяет все существующие строки и отклоняет BENZUP атомарно в PostgreSQL.
-- Стандартный SQL также позволяет выполнять ту же миграцию в H2-проверках.
ALTER TABLE data_source DROP CONSTRAINT IF EXISTS data_source_data_mode_check;
ALTER TABLE data_source
    ADD CONSTRAINT data_source_data_mode_check CHECK (data_mode IN ('OPEN_DATA', 'DEMO'));
