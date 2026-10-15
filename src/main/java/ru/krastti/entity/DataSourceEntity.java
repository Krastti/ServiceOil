package ru.krastti.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import ru.krastti.entity.PersistenceValues.DataMode;

/**
 * Таблица {@code data_source} хранит сведения об источниках цен на топливо.
 * Каждая запись содержит идентификатор и название источника, режим данных
 * ({@code OPEN_DATA} или {@code DEMO}), описание методики
 * и время последнего импорта. На источник ссылаются записи таблицы {@code regional_price}.
 */
@Entity
@Table(name = "data_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataSourceEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_mode", nullable = false, length = 16)
    private DataMode dataMode;

    @Column(nullable = false, length = 2000)
    private String methodology;

    @Column(name = "last_imported_at")
    private OffsetDateTime lastImportedAt;

    public DataSourceEntity(String id, String name, DataMode dataMode, String methodology) {
        this.id = id;
        this.name = name;
        this.dataMode = dataMode;
        this.methodology = methodology;
    }

    public void markImported(OffsetDateTime importedAt) { this.lastImportedAt = importedAt; }
}
