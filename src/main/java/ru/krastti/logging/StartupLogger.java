package ru.krastti.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Сообщает о завершении запуска после инициализации контекста и выполнения ApplicationRunner. */
@Component
public class StartupLogger {
    private static final Logger LOG = LoggerFactory.getLogger(StartupLogger.class);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        if (context.getParent() != null) return;

        var environment = context.getEnvironment();
        var profiles = environment.getActiveProfiles();
        if (profiles.length == 0) profiles = environment.getDefaultProfiles();
        var duration = event.getTimeTaken();
        Object elapsed = duration == null ? "unknown" : duration.toMillis();
        boolean scheduled = environment.getProperty("rosstat.weekly.schedule.enabled", Boolean.class, false);

        // Этот режим закрывает контекст сразу после SpringApplication.run: HTTP-готовность не обещаем.
        if (environment.containsProperty("import.file")) {
            LOG.info("CSV_IMPORT_COMPLETED application={} profiles={} durationMs={} mode=CSV_IMPORT scheduleEnabled={}",
                    environment.getProperty("spring.application.name", "ServiceOil"),
                    String.join(",", profiles), elapsed, scheduled);
            return;
        }

        var server = context instanceof WebServerApplicationContext web ? web.getWebServer() : null;
        LOG.info("APPLICATION_READY application={} profiles={} durationMs={} mode={} port={} scheduleEnabled={}",
                environment.getProperty("spring.application.name", "ServiceOil"), String.join(",", profiles), elapsed,
                server == null ? "NON_WEB" : "WEB", server == null ? "none" : server.getPort(), scheduled);
    }
}
