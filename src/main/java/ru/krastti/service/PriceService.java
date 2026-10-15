package ru.krastti.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.exception.*;
import ru.krastti.entity.PersistenceValues.DataMode;
import ru.krastti.entity.PersistenceValues.ImportStatus;
import ru.krastti.entity.dto.*;
import ru.krastti.repository.DataSourceRepository;
import ru.krastti.repository.ImportRunRepository;
import ru.krastti.repository.RegionalPriceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
@RequiredArgsConstructor
public class PriceService {

    private final DataSourceRepository sources;
    private final ImportRunRepository imports;
    private final RegionalPriceRepository prices;

    /**
     * Возвращает каталог доступных для источника регионов, видов топлива
     * и периодов наблюдений вместе с метаданными источника.
     *
     * @param source идентификатор источника данных
     * @return каталог и сведения об источнике
     * @throws UnknownSourceException если источник не зарегистрирован
     */
    public CatalogResponseDTO catalog(String source) {
        return new CatalogResponseDTO(metadata(source), prices.findRegions(source), prices.findFuels(source), prices.findAvailability(source));
    }

    /**
     * Возвращает опубликованные точки цены для региона и вида топлива за период.
     * Границы периода включаются; даты без наблюдений не дополняются.
     *
     * @param source идентификатор источника данных
     * @param region код региона
     * @param fuel код вида топлива
     * @param from начало периода
     * @param to конец периода
     * @return метаданные источника и найденные точки цены; список может быть пустым
     * @throws InvalidRequestException если начало позже конца или период длиннее 366 дней
     * @throws UnknownSourceException если источник не зарегистрирован
     * @throws UnknownCombinationException если для источника нет такого региона и топлива
     */
    public HistoryResponseDTO history(String source, String region, String fuel, LocalDate from, LocalDate to) {
        if(from.isAfter(to) || ChronoUnit.DAYS.between(from,to)>=366) {
            throw new InvalidRequestException("Выберите период от 1 до 366 дней: начало не позже конца");
        }

        var meta=metadata(source);
        requireCombination(source,region,fuel);
        return new HistoryResponseDTO(meta, region, fuel, from, to, points(source, region, fuel, from, to));
    }

    /**
     * Формирует сводку по точкам истории: последнюю цену и изменение
     * между первой и последней точками периода.
     *
     * @param source идентификатор источника данных
     * @param region код региона
     * @param fuel код вида топлива
     * @param from начало периода
     * @param to конец периода
     * @return сводка; если наблюдений нет, ценовые показатели равны {@code null},
     *         а если наблюдение одно, изменение не рассчитывается
     * @throws InvalidRequestException если начало позже конца или период длиннее 366 дней
     * @throws UnknownSourceException если источник не зарегистрирован
     * @throws UnknownCombinationException если для источника нет такого региона и топлива
     */
    public SummaryResponseDTO summary(
            String source, String region, String fuel,
            LocalDate from, LocalDate to) {
        var history = history(source, region, fuel, from, to);
        var points = history.points();

        if (points.isEmpty()) {
            return new SummaryResponseDTO(
                    history.metadata(), region, fuel, from, to,
                    0, null, null, null, null, null);
        }

        var firstPoint = points.getFirst();
        var lastPoint = points.getLast();
        BigDecimal delta = null;
        BigDecimal percent = null;

        // Для изменения нужны как минимум начальная и конечная точки.
        if (points.size() >= 2) {
            // Абсолютное изменение цены: последняя цена минус первая.
            delta = lastPoint.price().subtract(firstPoint.price());
            // Относительное изменение к начальной цене, округлённое до сотых процента.
            percent = delta.multiply(new BigDecimal("100"))
                    .divide(firstPoint.price(), 2, RoundingMode.HALF_UP);
        }

        return new SummaryResponseDTO(
                history.metadata(), region, fuel, from, to, points.size(),
                lastPoint.price(), firstPoint.date(), lastPoint.date(), delta, percent);
    }


    /**
     * Сравнивает историю цен двух регионов на общих датах наблюдений.
     * Числовая разница рассчитывается для последней общей даты.
     *
     * @param source идентификатор источника данных
     * @param regionA код первого региона
     * @param regionB код второго региона
     * @param fuel код вида топлива
     * @param from начало периода
     * @param to конец периода
     * @return истории обоих регионов, общие даты и сравнение; если общих дат нет,
     *         сравнение будет отсутствовать, а предупреждение объяснит причину
     * @throws InvalidRequestException если начало позже конца или период длиннее 366 дней
     * @throws UnknownSourceException если источник не зарегистрирован
     * @throws UnknownCombinationException если для источника нет региона с таким топливом
     */
    public CompareResponseDTO compare(
            String source, String regionA, String regionB, String fuel,
            LocalDate from, LocalDate to) {

        var firstHistory = history(source, regionA, fuel, from, to);
        var secondHistory = history(source, regionB, fuel, from, to);

        // Индексируем историю второго региона, чтобы находить цену по дате.
        Map<LocalDate, PricePoint> pointsBByDate = new HashMap<>();
        for (var point : secondHistory.points()) {
            pointsBByDate.put(point.date(), point);
        }

        // Сохраняем общие даты. Последняя найденная пара станет итоговым сравнением.
        List<LocalDate> commonDates = new ArrayList<>();
        Comparison latestComparison = null;
        for (var pointA : firstHistory.points()) {
            var pointB = pointsBByDate.get(pointA.date());

            if (pointB == null) {
                // Без цены второго региона эту дату сравнить нельзя.
                continue;
            }

            commonDates.add(pointA.date());
            // Разница считается как цена региона A минус цена региона B.
            latestComparison = new Comparison(
                    pointA.date(), pointA.price(), pointB.price(),
                    pointA.price().subtract(pointB.price()));
        }

        List<String> warnings = commonDates.isEmpty()
                ? List.of("Общие даты отсутствуют. Числовое сравнение недоступно.")
                : List.of();

        return new CompareResponseDTO(
                firstHistory.metadata(), regionA, regionB, fuel, from, to,
                firstHistory.points(), secondHistory.points(), commonDates,
                latestComparison, warnings);
    }


    /**
     * Рассчитывает бюджет закупки топлива по цене выбранного региона на точную дату.
     * Объём берётся из запроса: это либо заданное количество литров, либо расчёт по технике.
     * Если указан регион для сравнения, бюджет для него рассчитывается для того же объёма и даты.
     * При отсутствии источника используется ROSSTAT_PFO.
     *
     * @param request параметры расчёта: источник, регион, топливо, дата и объём или список техники
     * @return цена за литр, общий объём, рассчитанный бюджет и, если запрошено, сравнение регионов
     * @throws InvalidRequestException если объём или список техники заданы некорректно
     * @throws UnknownSourceException если источник не зарегистрирован
     * @throws UnknownCombinationException если для источника нет цен выбранного топлива в регионе
     * @throws PriceNotFoundException если на выбранную дату нет наблюдения цены
     */
    public EstimateResponseDTO estimate(EstimateRequestDTO request) {
        String sourceId = request.sourceId() == null
                ? "ROSSTAT_PFO"
                : request.sourceId();

        var metadata = metadata(sourceId);
        var totalLiters = totalLiters(request);
        var selectedPoint = exact(sourceId, request.regionId(), request.fuelCode(), request.priceDate());
        var estimatedCost = cost(totalLiters, selectedPoint.price());

        // Сравнение опционально: если регион не выбран, в ответе его не будет.
        BudgetComparison comparison = null;
        String comparisonRegionId = request.comparisonRegionId();

        if (comparisonRegionId != null) {
            // Берём цену второго региона на ту же дату и считаем для того же объёма.
            var comparisonPoint = exact(sourceId, comparisonRegionId, request.fuelCode(), request.priceDate());
            var comparisonCost = cost(totalLiters, comparisonPoint.price());

            // Положительная разница означает, что основной регион обойдётся дороже.
            var costDifference = estimatedCost.subtract(comparisonCost);

            comparison = new BudgetComparison(comparisonRegionId, comparisonPoint.price(), comparisonCost, costDifference);
        }

        return new EstimateResponseDTO(metadata, request.regionId(), request.fuelCode(), selectedPoint.date(),
                totalLiters, selectedPoint.price(), estimatedCost, selectedPoint.sourceUrl(), comparison);
    }


    private PricePoint exact(String source, String region, String fuel, LocalDate date) {
        requireCombination(source, region, fuel);
        var points = points(source, region, fuel, date, date);
        if (points.isEmpty()) {
            throw new PriceNotFoundException("На дату " + date + " нет цены для региона " + region + " и топлива " + fuel + ". Выберите дату наблюдения.");
        }
        return points.getFirst();
    }

    private void requireCombination(String source, String region, String fuel) {
        if (!prices.existsByIdSourceIdAndIdRegionCodeAndIdFuelCode(source, region, fuel)) {
            throw new UnknownCombinationException("Для источника " + source + " нет набора данных по региону " + region + " и топливу " + fuel);
        }
    }

    /**
     * Определяет общий объём топлива: из заданного количества литров
     * либо по недостающему объёму баков указанной техники.
     * Для каждой единицы техники учитываются количество машин и уже имеющееся топливо.
     *
     * @param request параметры объёма или перечень техники
     * @return рассчитанный объём в литрах
     * @throws InvalidRequestException если одновременно или ни одним способом не задан объём,
     *                                 список техники некорректен или итоговый объём вне допустимых границ
     */
    private static BigDecimal totalLiters(EstimateRequestDTO request) {
        boolean hasVolume = request.volumeLiters() != null;
        boolean hasVehicles = request.vehicles() != null;
        if (hasVolume == hasVehicles) {
            throw new InvalidRequestException("Укажите либо объём закупки, либо технику.");
        }

        BigDecimal totalLiters;
        if (hasVolume) {
            totalLiters = request.volumeLiters();
        } else {
            var vehicles = request.vehicles();
            if (vehicles.isEmpty() || vehicles.size() > 100) {
                throw new InvalidRequestException("Добавьте от 1 до 100 строк техники.");
            }

            totalLiters = BigDecimal.ZERO;
            for (var vehicle : vehicles) {
                if (vehicle.remainingLiters().compareTo(vehicle.tankCapacity()) > 0) {
                    throw new InvalidRequestException("Остаток не может превышать ёмкость бака.");
                }

                // Считаем, сколько нужно долить во все машины одного типа.
                var litersPerVehicle = vehicle.tankCapacity().subtract(vehicle.remainingLiters());
                totalLiters = totalLiters.add(
                        litersPerVehicle.multiply(BigDecimal.valueOf(vehicle.count())));
            }
        }

        if (totalLiters.signum() <= 0 || totalLiters.compareTo(new BigDecimal("1000000")) > 0) {
            throw new InvalidRequestException(
                    "Общий объём должен быть больше нуля и не более 1 000 000 литров.");
        }
        return totalLiters;
    }

    private static BigDecimal cost(BigDecimal liters, BigDecimal price) {
        return liters.multiply(price).setScale(2,RoundingMode.HALF_UP);
    }

    private Metadata metadata(String sourceId) {
        var source = sources.findById(sourceId).orElseThrow(UnknownSourceException::new);

        // Сохраняем прежний MIN по текстовым значениям, а не порядок enum.
        var dataFrequency = prices.findFrequencies(sourceId).stream()
                .map(Enum::name)
                .min(String::compareTo).orElse(null);
        var warnings = new ArrayList<String>();

        boolean isDemoSource = source.getDataMode() == DataMode.DEMO;
        if (isDemoSource) {
            warnings.add("Демонстрационные данные, цены вымышлены.");
        } else {
            warnings.add("Архивные опубликованные данные; цена региона оценивает бюджет и не гарантирует предложение АЗС.");
        }

        if ("MONTHLY".equals(dataFrequency)) {
            warnings.add("Месячные данные на конец периода; дневная история отсутствует.");
        }

        boolean latestImportFailed = imports.findFirstBySourceIdOrderByIdDesc(sourceId)
                .map(run -> run.getStatus() == ImportStatus.FAILED).orElse(false);
        if (latestImportFailed) {
            warnings.add("Последнее обновление не удалось. Показан ранее сохранённый набор.");
        }

        return new Metadata(source.getId(), source.getName(), source.getDataMode().name(),"LITER", "RUB",
                dataFrequency, source.getMethodology(), source.getLastImportedAt(), warnings);
    }

    private List<PricePoint> points(String source, String region, String fuel, LocalDate from, LocalDate to) {
        return prices.findPoints(source, region, fuel, from, to).stream()
                .map(point -> new PricePoint(point.getDate(), point.getPrice(), null, null, null, point.getSourceUrl()))
                .toList();
    }
}
