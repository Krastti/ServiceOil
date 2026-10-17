package ru.krastti.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.krastti.entity.DataSourceEntity;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, Integer> {
    Optional<DataSourceEntity> findByCode(String code);
}
