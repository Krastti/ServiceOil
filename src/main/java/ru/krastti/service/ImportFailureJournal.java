package ru.krastti.service;

import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import ru.krastti.entity.ImportRunEntity;
import ru.krastti.entity.PersistenceValues.ImportStatus;
import ru.krastti.repository.ImportRunRepository;

/** Записывает неуспех после отката основного импорта в независимой транзакции. */
@Service
@RequiredArgsConstructor
public class ImportFailureJournal {
    private final ImportRunRepository imports;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String source, OffsetDateTime started, int rejected) {
        record(source, started, rejected, "INVALID_IMPORT");
    }

    /** Фиксирует заранее определённый код сбоя, не записывая сообщение поставщика. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String source, OffsetDateTime started, int rejected, String code) {
        imports.saveAndFlush(new ImportRunEntity(source, started, OffsetDateTime.now(ZoneOffset.UTC),
                ImportStatus.FAILED, 0, Math.max(1, rejected), code));
    }
}
