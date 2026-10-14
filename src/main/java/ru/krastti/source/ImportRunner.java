package ru.krastti.source;

import lombok.RequiredArgsConstructor;

import ru.krastti.entity.dto.Source;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ru.krastti.service.CsvImportService;

import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "import.file")
@RequiredArgsConstructor
public class ImportRunner implements CommandLineRunner {
    private final CsvImportService importer;
    private final Environment environment;
    public void run(String... args) {
        var source = new Source(
                environment.getRequiredProperty("import.source-id"), environment.getRequiredProperty("import.source-name"),
                environment.getRequiredProperty("import.data-mode"), environment.getRequiredProperty("import.methodology"));
        int count = importer.importFile(Path.of(environment.getRequiredProperty("import.file")), source);
        System.out.println("IMPORT_OK rows=" + count);
    }
}
