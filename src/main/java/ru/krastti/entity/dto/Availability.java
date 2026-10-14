package ru.krastti.entity.dto;

import java.time.LocalDate;

/**
 * Диапазон опубликованных дат для сочетания региона и топлива в каталоге.
 * Промежуток между границами может содержать даты без наблюдений.
 *
 * @param regionId код региона
 * @param fuelCode код вида топлива
 * @param from первая дата наблюдения
 * @param to последняя дата наблюдения
 */
public record Availability(String regionId, String fuelCode, LocalDate from, LocalDate to) {}
