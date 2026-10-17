package ru.krastti.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import ru.krastti.entity.VehicleTemplateEntity;

/** Доступ к постоянному справочнику шаблонов техники. */
public interface VehicleTemplateRepository extends JpaRepository<VehicleTemplateEntity, Integer> {
    @EntityGraph(attributePaths = "fuel")
    List<VehicleTemplateEntity> findAllByOrderByNameAscIdAsc();
}
