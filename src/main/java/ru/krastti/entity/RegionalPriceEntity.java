package ru.krastti.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.krastti.entity.PersistenceValues.*;

/**
 * Таблица {@code regional_price} хранит историю региональных цен на топливо.
 * Каждая запись представляет цену из конкретного источника для одного региона,
 * вида топлива и даты наблюдения. Эта комбинация защищена UNIQUE, а PK — числовой id.
 * Дополнительно хранятся периодичность наблюдений, цена за литр в рублях,
 * ссылка на исходные данные и время импорта записи.
 * Источник, регион и вид топлива связаны со справочниками {@code data_source},
 * {@code region} и {@code fuel_type} внешними ключами.
 */
@Entity
@Table(name = "regional_price", uniqueConstraints = @UniqueConstraint(
        name = "uq_regional_price_observation", columnNames = {"source_id", "region_id", "fuel_type_id", "observed_on"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionalPriceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private DataSourceEntity source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private RegionEntity region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelTypeEntity fuel;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Frequency frequency;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Unit unit;

    @Column(name = "source_url", nullable = false, length = 2000)
    private String sourceUrl;

    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    public RegionalPriceEntity(DataSourceEntity source, RegionEntity region, FuelTypeEntity fuel,
                               LocalDate observedOn, Frequency frequency, BigDecimal price,
                               String sourceUrl, OffsetDateTime importedAt) {
        this.observedOn = observedOn;
        this.source = source;
        this.region = region;
        this.fuel = fuel;
        this.frequency = frequency;
        this.price = price;
        this.currency = Currency.RUB;
        this.unit = Unit.LITER;
        this.sourceUrl = sourceUrl;
        this.importedAt = importedAt;
    }

    /** Обновляет публикацию, сохраняя ключ и периодичность. */
    public void correct(BigDecimal price, String sourceUrl, OffsetDateTime importedAt) {
        this.price = price;
        this.sourceUrl = sourceUrl;
        this.importedAt = importedAt;
    }
}
