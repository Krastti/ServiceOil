package ru.krastti.entity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Нормализованная цена Росстата, подготовленная для преобразования во внутренний CSV. */
public record RosstatPriceRow(
        String regionCode,
        String regionName,
        String fuelCode,
        LocalDate observedOn,
        BigDecimal price,
        String sourceUrl) {}
