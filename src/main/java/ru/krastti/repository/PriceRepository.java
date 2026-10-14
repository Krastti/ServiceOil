package ru.krastti.repository;

import org.springframework.stereotype.Repository;
import ru.krastti.exception.ApiException;
import ru.krastti.entity.PersistenceValues.ImportStatus;
import ru.krastti.entity.dto.*;
import java.time.LocalDate;
import java.util.*;

/** Собирает DTO из JPA-запросов внутри транзакции вызывающего сервиса. */
@Repository
public class PriceRepository {
    private final DataSourceRepository sources;
    private final ImportRunRepository imports;
    private final RegionalPriceRepository prices;

    public PriceRepository(DataSourceRepository sources, ImportRunRepository imports,
                           RegionalPriceRepository prices) {
        this.sources = sources;
        this.imports = imports;
        this.prices = prices;
    }

    public Metadata metadata(String sourceId) {
        var source = sources.findById(sourceId)
                .orElseThrow(() -> new ApiException(404, "UNKNOWN_SOURCE", "Источник ещё не загружен."));
        // Сохраняем прежний MIN по текстовым значениям, а не порядок enum.
        var frequency = prices.findFrequencies(sourceId).stream().map(Enum::name)
                .min(String::compareTo).orElse(null);
        var warnings = new ArrayList<String>();
        if (source.getDataMode().name().equals("DEMO")) {
            warnings.add("Демонстрационные данные, цены вымышлены.");
        } else {
            warnings.add("Архивные опубликованные данные; цена региона оценивает бюджет и не гарантирует предложение АЗС.");
        }
        if ("MONTHLY".equals(frequency)) {
            warnings.add("Месячные данные на конец периода; дневная история отсутствует.");
        }
        if (imports.findFirstBySourceIdOrderByIdDesc(sourceId)
                .map(run -> run.getStatus() == ImportStatus.FAILED).orElse(false)) {
            warnings.add("Последнее обновление не удалось. Показан ранее сохранённый набор.");
        }
        return new Metadata(source.getId(), source.getName(), source.getDataMode().name(), "LITER", "RUB",
                frequency, source.getMethodology(), source.getLastImportedAt(), warnings);
    }

    public List<Availability> availability(String source) { return prices.findAvailability(source); }
    public List<Region> regions(String source) { return prices.findRegions(source); }
    public List<Fuel> fuels(String source) { return prices.findFuels(source); }

    public void requireCombination(String source, String region, String fuel) {
        if (!prices.existsByIdSourceIdAndIdRegionCodeAndIdFuelCode(source, region, fuel)) {
            throw new ApiException(404, "UNKNOWN_COMBINATION", "Для выбранного региона и топлива нет набора данных.");
        }
    }

    public List<PricePoint> points(String source, String region, String fuel, LocalDate from, LocalDate to) {
        return prices.findPoints(source, region, fuel, from, to).stream()
                .map(point -> new PricePoint(point.getDate(), point.getPrice(), null, null, null, point.getSourceUrl()))
                .toList();
    }
}
