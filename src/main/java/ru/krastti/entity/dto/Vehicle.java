package ru.krastti.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.math.BigDecimal;

/**
 * Строка техники с одинаковой ёмкостью бака и остатком для всех указанных единиц.
 * Объём заправки строки равен (ёмкость бака минус остаток) × количество.
 * Остаток не может превышать ёмкость бака.
 *
 * @param name название техники, от 1 до 100 символов, не состоящее только из пробелов
 * @param tankCapacity ёмкость одного бака в литрах, больше нуля и не более 1 000 000
 * @param remainingLiters остаток в одном баке в литрах, от нуля до ёмкости бака
 * @param count целое количество единиц техники, от 1 до 1000
 */
public record Vehicle(@NotBlank @Size(max=100) String name,
                      @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("1000000") BigDecimal tankCapacity,
                      @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal remainingLiters,
                      @NotNull @Min(1) @Max(1000) Integer count) {}
