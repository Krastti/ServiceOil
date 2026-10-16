package ru.krastti.entity.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Строка парка: ёмкость берётся сервером из шаблона, топливо выбирается для этой строки. */
public record FleetVehicleRequestDTO(
        @NotNull @Positive Long templateId,
        @NotNull @Pattern(regexp = "DIESEL|AI92") String fuelCode,
        @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal remainingLiters,
        @NotNull @Min(1) @Max(1000) Integer count) {}
