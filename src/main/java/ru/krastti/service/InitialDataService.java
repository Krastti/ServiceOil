package ru.krastti.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.repository.ImportAdvisoryLock;
import ru.krastti.repository.RegionalPriceRepository;

/** Загружает поставляемые цены только в пустую БД, используя общий валидатор CSV. */
@Service
@RequiredArgsConstructor
public class InitialDataService {
    private final RegionalPriceRepository prices;
    private final ImportAdvisoryLock locks;
    private final CsvImportService importer;

    /** Блокировка действует до завершения проверки и отдельной транзакции импорта. */
    @Transactional
    public void importIfEmpty(Resource csv) throws IOException {
        locks.acquire();
        if (prices.count() > 0) {
            LoggerFactory.getLogger(InitialDataService.class)
                    .info("INITIAL_DATA_IMPORT_SKIPPED reason=prices_present");
            return;
        }
        try (var input = csv.getInputStream()) {
            var content = new String(input.readNBytes(10_000_001), StandardCharsets.UTF_8);
            int rows = importer.importCsv(content, RosstatWeeklyImportService.SOURCE);
            LoggerFactory.getLogger(InitialDataService.class)
                    .info("INITIAL_DATA_IMPORT_SUCCESS rows={}", rows);
        }
    }
}
