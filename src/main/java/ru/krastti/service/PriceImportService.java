package ru.krastti.service;

import lombok.RequiredArgsConstructor;

import jakarta.persistence.EntityManager;
import ru.krastti.exception.InvalidImportException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import ru.krastti.entity.*;
import ru.krastti.entity.PersistenceValues.*;
import ru.krastti.entity.dto.Source;
import ru.krastti.entity.dto.Row;
import ru.krastti.repository.*;

/** Сохраняет весь CSV атомарно; вызывается через отдельный Spring bean. */
@Service
@RequiredArgsConstructor
public class PriceImportService {
    private final EntityManager entities;
    private final DataSourceRepository sources;
    private final RegionRepository regions;
    private final FuelTypeRepository fuels;
    private final RegionalPriceRepository prices;
    private final ImportRunRepository imports;

    /** Координирует атомарную запись CSV: подготовку данных, строки и журнал успеха. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, timeout = 60)
    public void write(Source source, List<Row> rows, OffsetDateTime started) {
        var context = prepareImport(source, rows);
        importRows(context, rows, started);
        completeImport(context, rows.size(), started);
    }

    /** Загружает и проверяет справочник топлива под общей блокировкой импорта. */
    private Map<String, FuelTypeEntity> loadLockedFuelTypes() {
        var fuelByCode = new HashMap<String, FuelTypeEntity>();
        fuels.lockForImport().forEach(f -> fuelByCode.put(f.getCode(), f));
        require(fuelByCode.keySet().containsAll(List.of("AI92", "DIESEL")));
        return fuelByCode;
    }

    /** Подготавливает источник и справочные данные, необходимые для записи строк. */
    private ImportContext prepareImport(Source source, List<Row> rows) {
        var fuelByCode = loadLockedFuelTypes();
        entities.unwrap(Session.class).setJdbcBatchSize(50);
        var dataSource = findOrCreateDataSource(source);
        var frequency = Frequency.valueOf(rows.getFirst().frequency());
        var existingPrices = loadExistingPrices(source.id(), frequency);
        var regionByCode = loadRegions(rows);

        return new ImportContext(source, dataSource, frequency, fuelByCode, existingPrices, regionByCode);
    }

    /** Находит источник или создаёт его, проверяя неизменность его метаданных. */
    private DataSourceEntity findOrCreateDataSource(Source source) {
        var dataSource = sources.findById(source.id()).orElse(null);
        if (dataSource == null) {
            dataSource = new DataSourceEntity(source.id(), source.name(), DataMode.valueOf(source.dataMode()), source.methodology());
            entities.persist(dataSource);
        } else {
            require(dataSource.getName().equals(source.name()) && dataSource.getDataMode().name().equals(source.dataMode())
                    && dataSource.getMethodology().equals(source.methodology()));
        }

        return dataSource;
    }

    /** Загружает цены источника и проверяет, что их периодичность совпадает с CSV. */
    private Map<RegionalPriceId, RegionalPriceEntity> loadExistingPrices(String sourceId, Frequency frequency) {
        var existingPrices = new HashMap<RegionalPriceId, RegionalPriceEntity>();
        for (var price : prices.findAllByIdSourceId(sourceId)) {
            require(price.getFrequency() == frequency);
            existingPrices.put(price.getId(), price);
        }

        return existingPrices;
    }

    /** Пакетами загружает регионы, которые встречаются в строках CSV. */
    private Map<String, RegionEntity> loadRegions(List<Row> rows) {
        var regionByCode = new HashMap<String, RegionEntity>();
        var codes = rows.stream().map(Row::region).distinct().toList();
        for (int offset = 0; offset < codes.size(); offset += 500) {
            regions.findAllById(codes.subList(offset, Math.min(offset + 500, codes.size())))
                    .forEach(r -> regionByCode.put(r.getCode(), r));
        }

        return regionByCode;
    }

    /** Применяет строки импорта и периодически отправляет накопленные изменения в БД. */
    private void importRows(ImportContext context, List<Row> rows, OffsetDateTime started) {
        int processed = 0;
        for (var row : rows) {
            upsertRow(context, row, started);
            // Flush не коммитит: следующая ошибка откатывает и уже отправленные пачки.
            // Контекст сохраняем до commit для dirty checking загруженных цен.
            if (++processed % 100 == 0) entities.flush();
        }
    }

    /** Создаёт регион при необходимости и добавляет или исправляет цену одной строки. */
    private void upsertRow(ImportContext context, Row row, OffsetDateTime started) {
        var region = context.regionByCode().get(row.region());
        if (region == null) {
            region = new RegionEntity(row.region(), row.name());
            entities.persist(region);
            context.regionByCode().put(row.region(), region);
        } else {
            require(region.getName().equals(row.name()));
        }

        var key = new RegionalPriceId(context.source().id(), row.region(), row.fuel(), row.date());
        var previous = context.existingPrices().get(key);
        if (previous == null) {
            var price = new RegionalPriceEntity(context.dataSource(), region, context.fuelByCode().get(row.fuel()),
                    row.date(), context.frequency(), row.price(), row.url(), started);
            entities.persist(price);
            context.existingPrices().put(key, price);
        } else {
            previous.correct(row.price(), row.url(), started);
        }
    }

    /** Отмечает источник импортированным и сохраняет успешную попытку импорта. */
    private void completeImport(ImportContext context, int rowCount, OffsetDateTime started) {
        context.dataSource().markImported(started);
        entities.flush();
        imports.saveAndFlush(new ImportRunEntity(context.source().id(), started, OffsetDateTime.now(ZoneOffset.UTC),
                ImportStatus.SUCCESS, rowCount, 0, null));
    }

    private record ImportContext(
            Source source,
            DataSourceEntity dataSource,
            Frequency frequency,
            Map<String, FuelTypeEntity> fuelByCode,
            Map<RegionalPriceId, RegionalPriceEntity> existingPrices,
            Map<String, RegionEntity> regionByCode) {
    }

    /** Прерывает импорт, если нарушено обязательное условие целостности данных. */
    private static void require(boolean condition) {
        if (!condition) throw new InvalidImportException();
    }
}
