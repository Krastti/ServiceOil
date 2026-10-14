package ru.krastti.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.krastti.entity.RegionEntity;

public interface RegionRepository extends JpaRepository<RegionEntity, String> {}
