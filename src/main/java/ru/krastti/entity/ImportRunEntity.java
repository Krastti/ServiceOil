package ru.krastti.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.krastti.entity.PersistenceValues.ImportStatus;

/**
 * Таблица {@code import_run} хранит журнал попыток импорта цен.
 * Каждая запись содержит идентификатор попытки, идентификатор источника,
 * время начала и завершения, результат ({@code SUCCESS} или {@code FAILED}),
 * количество импортированных и отклонённых строк и код ошибки при её наличии.
 * Код источника не связан внешним ключом с {@code data_source},
 * чтобы можно было сохранить ошибку импорта даже для неизвестного источника.
 */
@Entity
@Table(name = "import_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportRunEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "source_code", nullable = false, length = 64)
    private String sourceCode;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private OffsetDateTime finishedAt;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ImportStatus status;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    public ImportRunEntity(String sourceCode, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                           ImportStatus status, int rowCount, int rejectedCount, String errorCode) {
        this.sourceCode = sourceCode;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.rowCount = rowCount;
        this.rejectedCount = rejectedCount;
        this.errorCode = errorCode;
    }
}
