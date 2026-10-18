package ru.krastti.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import ru.krastti.service.InitialDataService;

/** Автозагрузка снимка данных после миграций; явный CSV-импорт имеет приоритет. */
@Configuration
@ConditionalOnProperty(name = "serviceoil.seed.enabled", havingValue = "true")
public class InitialDataConfiguration {
    @Bean
    ApplicationRunner initialData(InitialDataService initializer,
            @Value("${serviceoil.seed.file}") Resource csv,
            @Value("${import.file:}") String explicitImport) {
        return arguments -> {
            if (explicitImport.isBlank()) initializer.importIfEmpty(csv);
        };
    }
}
