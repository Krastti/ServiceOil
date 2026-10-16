package ru.krastti.entity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Итоги по парку и отдельные расчёты по топливу; сравнение отсутствует, если регион не задан. */
public record FleetEstimateResponseDTO(String regionId, LocalDate priceDate, BigDecimal totalLiters,
        BigDecimal estimatedCost, String comparisonRegionId, BigDecimal comparisonEstimatedCost,
        BigDecimal difference, List<EstimateResponseDTO> fuelBudgets) {}
