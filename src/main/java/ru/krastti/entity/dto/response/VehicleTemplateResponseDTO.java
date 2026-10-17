package ru.krastti.entity.dto.response;

import java.math.BigDecimal;

/** Публичный шаблон техники для выпадающего списка калькулятора. */
public record VehicleTemplateResponseDTO(Long id, String name, BigDecimal tankCapacity, String fuelCode) {}
