package ru.krastti.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.exception.RosstatSourceException;
import ru.krastti.repository.ImportAdvisoryLock;

/** Общая блокировка ручного и автоматического импорта; запись цен остаётся в CsvImportService. */
@Service
@RequiredArgsConstructor
public class RosstatImportCoordinator {
    public static final String SOURCE_ID = "ROSSTAT_PFO_WEEKLY";
    private final ImportAdvisoryLock locks;
    private final RosstatWeeklyImportService importer;
    private final ImportFailureJournal failures;

    @Transactional
    public RosstatWeeklyImportService.ImportResult importRange(LocalDate from, LocalDate to) {
        return run(() -> importer.importRange(from, to));
    }

    @Transactional
    public RosstatWeeklyImportService.ImportResult importRecent() {
        return run(importer::importNewPublications);
    }

    private RosstatWeeklyImportService.ImportResult run(Supplier<RosstatWeeklyImportService.ImportResult> action) {
        locks.acquire();
        var started = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            return action.get();
        } catch (RosstatSourceException failure) {
            failures.record(SOURCE_ID, started, 1, failure.code());
            throw failure;
        }
    }
}
