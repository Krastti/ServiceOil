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
import ru.krastti.repository.*;

/** Сохраняет весь CSV атомарно; вызывается через отдельный Spring bean. */
@Service
@RequiredArgsConstructor
public class JpaImportWriter {
    private final EntityManager entities;
    private final DataSourceRepository sources;
    private final RegionRepository regions;
    private final FuelTypeRepository fuels;
    private final RegionalPriceRepository prices;
    private final ImportRunRepository imports;


    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, timeout = 60)
    public void write(Source source, List<CsvImportService.Row> rows, OffsetDateTime started) {
        // Flyway заранее создаёт топливо. Общая блокировка защищает создание
        // источников и регионов даже между разными процессами приложения.
        var fuelByCode = new HashMap<String, FuelTypeEntity>();
        fuels.lockForImport().forEach(f -> fuelByCode.put(f.getCode(), f));
        require(fuelByCode.keySet().containsAll(List.of("AI92", "DIESEL")));
        entities.unwrap(Session.class).setJdbcBatchSize(50);
        var dataSource = sources.findById(source.id()).orElse(null);
        if (dataSource == null) {
            dataSource = new DataSourceEntity(source.id(), source.name(), DataMode.valueOf(source.dataMode()), source.methodology());
            entities.persist(dataSource);
        } else {
            require(dataSource.getName().equals(source.name()) && dataSource.getDataMode().name().equals(source.dataMode())
                    && dataSource.getMethodology().equals(source.methodology()));
        }
        var frequency = Frequency.valueOf(rows.getFirst().frequency());
        Map<RegionalPriceId, RegionalPriceEntity> existing = new HashMap<>();
        for (var price : prices.findAllByIdSourceId(source.id())) {
            require(price.getFrequency() == frequency);
            existing.put(price.getId(), price);
        }
        var regionByCode = new HashMap<String, RegionEntity>();
        var codes = rows.stream().map(CsvImportService.Row::region).distinct().toList();
        for (int offset = 0; offset < codes.size(); offset += 500) {
            regions.findAllById(codes.subList(offset, Math.min(offset + 500, codes.size())))
                    .forEach(r -> regionByCode.put(r.getCode(), r));
        }
        int processed = 0;
        for (var row : rows) {
            var region = regionByCode.get(row.region());
            if (region == null) {
                region = new RegionEntity(row.region(), row.name());
                entities.persist(region);
                regionByCode.put(row.region(), region);
            } else {
                require(region.getName().equals(row.name()));
            }
            var key = new RegionalPriceId(source.id(), row.region(), row.fuel(), row.date());
            var previous = existing.get(key);
            if (previous == null) {
                var price = new RegionalPriceEntity(dataSource, region, fuelByCode.get(row.fuel()), row.date(),
                        frequency, row.price(), row.url(), started);
                entities.persist(price);
                existing.put(key, price);
            } else {
                previous.correct(row.price(), row.url(), started);
            }
            // Flush не коммитит: следующая ошибка откатывает и уже отправленные пачки.
            // Контекст сохраняем до commit для dirty checking загруженных цен.
            if (++processed % 100 == 0) entities.flush();
        }
        dataSource.markImported(started);
        entities.flush();
        imports.saveAndFlush(new ImportRunEntity(source.id(), started, OffsetDateTime.now(ZoneOffset.UTC),
                ImportStatus.SUCCESS, rows.size(), 0, null));
    }

    private static void require(boolean condition) {
        if (!condition) throw new InvalidImportException();
    }
}
