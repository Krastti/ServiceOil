package ru.krastti.entity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Запрос расчёта бюджета заправки; цену выбирает сервер из базы данных.
 * Нужно задать ровно один режим: объём закупки или список техники.
 * Суммарный объём должен быть больше нуля и не превышать 1 000 000 литров.
 *
 * @param sourceId идентификатор источника; при null используется ROSSTAT_PFO
 * @param regionId код основного региона
 * @param fuelCode код вида топлива
 * @param priceDate точная дата цены, без подстановки соседних дат
 * @param comparisonRegionId код дополнительного региона; null, если сравнение не требуется
 * @param volumeLiters объём закупки в литрах; null при расчёте по технике
 * @param vehicles от 1 до 100 строк техники; null при заданном объёме закупки
 */
public record EstimateRequest(String sourceId, @NotBlank String regionId, @NotBlank String fuelCode,
                              @NotNull LocalDate priceDate, String comparisonRegionId,
                              @DecimalMin(value="0",inclusive=false) @DecimalMax("1000000") BigDecimal volumeLiters,
                              @Size(min=1,max=100) List<@NotNull @Valid Vehicle> vehicles) {}
