CREATE TABLE scheduled_import_lock (
    name VARCHAR(64) PRIMARY KEY
);
INSERT INTO scheduled_import_lock(name) VALUES ('ROSSTAT_PFO_WEEKLY');
