package ru.krastti.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.krastti.entity.DataSourceEntity;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, String> {}
