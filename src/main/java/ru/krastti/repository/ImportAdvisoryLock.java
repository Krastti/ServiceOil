package ru.krastti.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Общая для экземпляров приложения блокировка на время внешней транзакции импорта. */
@Repository
@RequiredArgsConstructor
public class ImportAdvisoryLock {
    private final EntityManager entities;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        var acquired = (Boolean) entities.createNativeQuery(
                "select pg_try_advisory_xact_lock(1397705036, 1)", Boolean.class).getSingleResult();
        if (!acquired) throw new CannotAcquireLockException("IMPORT_BUSY");
    }
}
