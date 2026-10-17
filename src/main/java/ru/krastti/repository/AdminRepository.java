package ru.krastti.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import ru.krastti.entity.AdminEntity;

/** Поиск учётной записи для стандартной аутентификации Spring Security. */
public interface AdminRepository extends JpaRepository<AdminEntity, Integer> {
    Optional<AdminEntity> findByUsername(String username);
    boolean existsByUsernameAndIdNot(String username, Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select admin from AdminEntity admin where admin.username = :username")
    Optional<AdminEntity> findForUpdate(@Param("username") String username);
}
