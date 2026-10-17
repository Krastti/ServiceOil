package ru.krastti.entity.dto.response;

import ru.krastti.entity.dto.BudgetComparison;
import ru.krastti.entity.dto.Metadata;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ответ серверного расчёта бюджета заправки по цене из базы на точную дату.
 * Итоговая стоимость округляется до копеек по правилу HALF_UP.
 *
 * @param metadata сведения об источнике выбранной цены
 * @param regionId код основного региона
 * @param fuelCode код вида топлива
 * @param priceDate точная дата опубликованной цены
 * @param totalLiters общий требуемый объём, литры
 * @param pricePerLiter цена литра в основном регионе, рубли
 * @param estimatedCost стоимость общего объёма в основном регионе, рубли
 * @param sourceUrl ссылка на исходные данные цены
 * @param comparison расчёт для другого региона; null, если сравнение не запрошено
 */
public record EstimateResponseDTO(Metadata metadata, String regionId, String fuelCode, LocalDate priceDate,
                                  BigDecimal totalLiters, BigDecimal pricePerLiter, BigDecimal estimatedCost,
                                  String sourceUrl, BudgetComparison comparison) {}
