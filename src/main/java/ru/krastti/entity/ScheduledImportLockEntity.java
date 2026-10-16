package ru.krastti.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Постоянная строка блокировки импорта; блокировка транзакции снимается также при падении процесса. */
@Entity
@Table(name = "scheduled_import_lock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledImportLockEntity {
    @Id
    @Column(name = "name", length = 64)
    private String name;
}
