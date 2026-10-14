package ru.krastti.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import ru.krastti.entity.FuelTypeEntity;

public interface FuelTypeRepository extends JpaRepository<FuelTypeEntity, String> {
    /** Общая блокировка импорта, включая создание новых источников и регионов. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "30000"))
    @Query("select f from FuelTypeEntity f order by f.code")
    List<FuelTypeEntity> lockForImport();
}
