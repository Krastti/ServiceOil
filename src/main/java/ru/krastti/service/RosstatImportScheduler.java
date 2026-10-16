package ru.krastti.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.krastti.exception.RosstatSourceException;

/** Плановый запуск продолжает работать после сбоя; в лог попадают только безопасные коды и счётчики. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rosstat.weekly.schedule.enabled", havingValue = "true")
public class RosstatImportScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(RosstatImportScheduler.class);
    private final RosstatImportCoordinator imports;

    @Scheduled(cron = "${rosstat.weekly.schedule.cron:0 0 10 * * THU}",
            zone = "${rosstat.weekly.schedule.zone:Europe/Moscow}")
    public void run() {
        try {
            var result = imports.importRecent();
            LOG.info("ROSSTAT_IMPORT_SUCCESS publications={} rows={}", result.publicationsFound(), result.rowsImported());
        } catch (PessimisticLockingFailureException busy) {
            LOG.info("ROSSTAT_IMPORT_SKIPPED_BUSY");
        } catch (RosstatSourceException failure) {
            LOG.warn("ROSSTAT_IMPORT_FAILED code={}", failure.code());
        } catch (RuntimeException failure) {
            LOG.warn("ROSSTAT_IMPORT_FAILED code=IMPORT_FAILED");
        }
    }
}
