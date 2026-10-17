package ru.krastti.entity.dto.response;

import ru.krastti.entity.dto.Metadata;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Сводка изменения опубликованной цены за выбранный период.
 * При отсутствии наблюдений цены и даты равны null; при одной точке изменение не вычисляется.
 *
 * @param metadata сведения об источнике и ограничения данных
 * @param regionId код региона
 * @param fuelCode код вида топлива
 * @param from начало запрошенного периода включительно
 * @param to конец запрошенного периода включительно
 * @param pointCount количество опубликованных наблюдений в периоде
 * @param price цена последнего наблюдения, рубли за литр; null при пустой истории
 * @param firstDate дата первого наблюдения; null при пустой истории
 * @param lastDate дата последнего наблюдения; null при пустой истории
 * @param delta последняя цена минус первая, рубли за литр; null при числе точек меньше двух
 * @param deltaPercent изменение относительно первой цены в процентах с двумя знаками; null при числе точек меньше двух
 */
public record SummaryResponseDTO(Metadata metadata, String regionId, String fuelCode, LocalDate from, LocalDate to,
                                 int pointCount, BigDecimal price, LocalDate firstDate, LocalDate lastDate, BigDecimal delta, BigDecimal deltaPercent) {}
