package ru.krastti.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.krastti.entity.RegionEntity;

public interface RegionRepository extends JpaRepository<RegionEntity, Integer> {
    List<RegionEntity> findByCodeIn(Collection<String> codes);
}
