package ru.krastti.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import ru.krastti.entity.ScheduledImportLockEntity;

/** Межпроцессная блокировка без ожидания: занятый импорт пропускается, а не ставится в очередь. */
public interface ScheduledImportLockRepository extends JpaRepository<ScheduledImportLockEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select l from ScheduledImportLockEntity l where l.name = 'ROSSTAT_PFO_WEEKLY'")
    Optional<ScheduledImportLockEntity> lockWeeklyImport();
}
