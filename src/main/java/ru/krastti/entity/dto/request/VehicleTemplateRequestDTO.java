package ru.krastti.entity.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Поля создания и редактирования шаблона; изменять справочник может только администратор. */
public record VehicleTemplateRequestDTO(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000")
        @Digits(integer = 7, fraction = 2) BigDecimal tankCapacity,
        @NotNull @Pattern(regexp = "DIESEL|AI92") String fuelCode) {}
