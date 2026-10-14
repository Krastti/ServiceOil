package ru.krastti.entity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Одна опубликованная цена в истории выбранного региона и вида топлива.
 * Неизвестные статистики АЗС передаются как null; текущий региональный источник их не заполняет.
 *
 * @param date дата наблюдения
 * @param price цена за литр в рублях
 * @param stationCount количество АЗС в выборке; null, если неизвестно
 * @param minPrice минимальная цена литра в выборке; null, если неизвестна
 * @param maxPrice максимальная цена литра в выборке; null, если неизвестна
 * @param sourceUrl ссылка на исходные данные наблюдения
 */
public record PricePoint(LocalDate date, BigDecimal price, Integer stationCount,
                         BigDecimal minPrice, BigDecimal maxPrice, String sourceUrl) {}
