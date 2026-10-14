package ru.krastti.entity.dto;

import java.math.BigDecimal;

/**
 * Стоимость того же объёма топлива в другом регионе на ту же дату.
 * Используется как дополнительное сравнение в результате расчёта заправки.
 *
 * @param regionId код региона сравнения
 * @param pricePerLiter цена литра в регионе сравнения, рубли
 * @param estimatedCost стоимость общего объёма в регионе сравнения, рубли
 * @param difference стоимость в основном регионе минус стоимость в регионе сравнения, рубли
 */
public record BudgetComparison(String regionId, BigDecimal pricePerLiter, BigDecimal estimatedCost, BigDecimal difference) {}
