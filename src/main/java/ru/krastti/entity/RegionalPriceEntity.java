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
 * вида топлива и даты наблюдения. Эта комбинация образует составной первичный ключ.
 * Дополнительно хранятся периодичность наблюдений, цена за литр в рублях,
 * ссылка на исходные данные и время импорта записи.
 * Источник, регион и вид топлива связаны со справочниками {@code data_source},
 * {@code region} и {@code fuel_type} внешними ключами.
 */
@Entity
@Table(name = "regional_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionalPriceEntity {
    @EmbeddedId
    private RegionalPriceId id;
    @MapsId("sourceId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private DataSourceEntity source;
    @MapsId("regionCode") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_code", nullable = false)
    private RegionEntity region;
    @MapsId("fuelCode") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fuel_code", nullable = false)
    private FuelTypeEntity fuel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Frequency frequency;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 3)
    private Currency currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Unit unit;
    @Column(name = "source_url", nullable = false, length = 2000)
    private String sourceUrl;
    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    public RegionalPriceEntity(DataSourceEntity source, RegionEntity region, FuelTypeEntity fuel,
                               LocalDate observedOn, Frequency frequency, BigDecimal price,
                               String sourceUrl, OffsetDateTime importedAt) {
        this.id = new RegionalPriceId(source.getId(), region.getCode(), fuel.getCode(), observedOn);
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
