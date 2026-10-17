package ru.krastti.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.krastti.entity.ImportRunEntity;

public interface ImportRunRepository extends JpaRepository<ImportRunEntity, Integer> {
    Optional<ImportRunEntity> findFirstBySourceCodeOrderByIdDesc(String sourceId);
}
