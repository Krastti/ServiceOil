package ru.krastti.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Плановый запуск продолжает работать после сбоя; результат записывает аспект координатора. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rosstat.weekly.schedule.enabled", havingValue = "true")
public class RosstatImportScheduler {
    private final RosstatImportCoordinator imports;

    @Scheduled(cron = "${rosstat.weekly.schedule.cron:0 0 10 * * THU}",
            zone = "${rosstat.weekly.schedule.zone:Europe/Moscow}")
    public void run() {
        try {
            imports.importRecent();
        } catch (RuntimeException failure) {
            // Аспект уже записал исход. Следующий запуск расписания должен состояться.
        }
    }
}
