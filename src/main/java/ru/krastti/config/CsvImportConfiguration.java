package ru.krastti.config;

import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.krastti.entity.dto.Source;
import ru.krastti.service.CsvImportService;

/** Одноразовый импорт поставляемого CSV для контейнера import; обычный веб-запуск данных не загружает. */
@Configuration
@ConditionalOnProperty(name = "import.file")
public class CsvImportConfiguration {
    @Bean
    ApplicationRunner importCsv(CsvImportService importer,
            @Value("${import.file}") String file,
            @Value("${import.source-id}") String sourceId,
            @Value("${import.source-name}") String name,
            @Value("${import.data-mode}") String mode,
            @Value("${import.methodology}") String methodology) {
        return arguments -> {
            int rows = importer.importFile(Path.of(file), new Source(sourceId, name, mode, methodology));
            LoggerFactory.getLogger(CsvImportConfiguration.class).info("CSV_IMPORT_SUCCESS rows={}", rows);
        };
    }
}
