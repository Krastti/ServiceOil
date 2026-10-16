package ru.krastti.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.krastti.entity.AdminEntity;

/** Поиск учётной записи для стандартной аутентификации Spring Security. */
public interface AdminRepository extends JpaRepository<AdminEntity, Long> {
    Optional<AdminEntity> findByUsername(String username);
}
