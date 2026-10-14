package ru.krastti.controller;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.krastti.entity.dto.*;
import ru.krastti.service.PriceService;

/**
 * HTTP API просмотра региональных цен на топливо и расчёта бюджета заправки.
 * Все операции принимают POST-запросы с JSON-телом по базовому пути {@code /api/v1}.
 * Входные DTO проверяются через {@link Valid}, а обработка данных делегируется
 * {@link PriceService}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PriceController {
    private final PriceService service;

    /**
     * Возвращает каталог регионов, видов топлива и доступных периодов наблюдений.
     * Если источник не указан в JSON-теле, используется {@code ROSSTAT_PFO}.
     *
     * @param request запрос с идентификатором источника данных
     * @return каталог доступных данных и сведения об источнике
     */
    @PostMapping("/catalog")
    public CatalogResponseDTO catalog(@Valid @RequestBody CatalogRequestDTO request) {
        return service.catalog(request.sourceId());
    }
    /**
     * Возвращает историю цен выбранного региона и вида топлива за заданный период.
     * Обе границы периода включаются; даты без наблюдений не дополняются ценами.
     *
     * @param request источник, регион, вид топлива и границы периода
     * @return наблюдения по возрастанию даты; при отсутствии данных список пуст
     */
    @PostMapping("/prices/history")
    public HistoryResponseDTO history(@Valid @RequestBody PricePeriodRequestDTO request) {
        return service.history(request.sourceId(),request.regionId(),request.fuelCode(),request.from(),request.to());
    }
    /**
     * Возвращает сводку цен региона за заданный период: последнюю цену,
     * количество наблюдений и изменение между первой и последней точками.
     *
     * @param request источник, регион, вид топлива и границы периода
     * @return сводка; при отсутствии цен числовые показатели цены равны {@code null},
     *         а при менее чем двух наблюдениях изменение не рассчитывается
     */
    @PostMapping("/prices/summary")
    public SummaryResponseDTO summary(@Valid @RequestBody PricePeriodRequestDTO request) {
        return service.summary(request.sourceId(),request.regionId(),request.fuelCode(),request.from(),request.to());
    }
    /**
     * Сравнивает цены двух регионов для одного источника, вида топлива и периода.
     * Числовое сравнение выполняется на последней общей дате наблюдений.
     *
     * @param request источник, два региона, вид топлива и границы периода
     * @return истории обоих регионов, общие даты и результат сравнения;
     *         при отсутствии общих дат результат сравнения равен {@code null}
     */
    @PostMapping("/prices/compare")
    public CompareResponseDTO compare(@Valid @RequestBody CompareRequestDTO request) {
        return service.compare(request.sourceId(),request.regionA(),request.regionB(),request.fuelCode(),request.from(),request.to());
    }
    /**
     * Рассчитывает бюджет закупки заданного объёма топлива или заправки списка техники.
     * Цена выбирается сервером из сохранённых данных строго на указанную дату.
     * При указании дополнительного региона сравнивает стоимость того же объёма.
     *
     * @param request источник, регион, топливо, дата цены и ровно один режим расчёта:
     *                объём закупки либо список техники
     * @return общий объём, цена за литр, итоговая стоимость и необязательное сравнение бюджетов
     */
    @PostMapping("/refueling/estimate")
    public EstimateResponseDTO estimate(@Valid @RequestBody EstimateRequestDTO request) { return service.estimate(request); }
}
