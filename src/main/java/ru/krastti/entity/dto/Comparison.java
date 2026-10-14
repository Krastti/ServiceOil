package ru.krastti.entity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Разница цен двух регионов на одной общей дате наблюдения.
 *
 * @param date общая дата наблюдения
 * @param priceA цена литра в первом регионе, рубли
 * @param priceB цена литра во втором регионе, рубли
 * @param difference цена первого региона минус цена второго региона, рубли за литр
 */
public record Comparison(LocalDate date, BigDecimal priceA, BigDecimal priceB, BigDecimal difference) {}
