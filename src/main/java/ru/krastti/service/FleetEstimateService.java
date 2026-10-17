package ru.krastti.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.entity.VehicleTemplateEntity;
import ru.krastti.entity.dto.request.EstimateRequestDTO;
import ru.krastti.entity.dto.request.FleetEstimateRequestDTO;
import ru.krastti.entity.dto.request.FleetVehicleRequestDTO;
import ru.krastti.entity.dto.response.EstimateResponseDTO;
import ru.krastti.entity.dto.response.FleetEstimateResponseDTO;
import ru.krastti.exception.ApiException;
import ru.krastti.exception.InvalidRequestException;
import ru.krastti.logging.LogOperation;
import ru.krastti.repository.VehicleTemplateRepository;

/** Рассчитывает смешанный парк по сохранённым ёмкостям и точным региональным ценам. */
@Service
@RequiredArgsConstructor
public class FleetEstimateService {
    private final VehicleTemplateRepository templates;
    private final PriceService prices;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @LogOperation(value = "refueling.fleet-estimate", warnIfSlow = true)
    public FleetEstimateResponseDTO estimate(FleetEstimateRequestDTO request) {
        var ids = request.vehicles().stream().map(FleetVehicleRequestDTO::templateId).distinct().toList();
        if (ids.stream().anyMatch(id -> id > Integer.MAX_VALUE)) {
            throw new ApiException(404, "UNKNOWN_VEHICLE_TEMPLATE", "Шаблон техники не найден. Обновите список.");
        }
        var saved = templates.findAllById(ids.stream().map(Long::intValue).toList()).stream()
                .collect(Collectors.toMap(VehicleTemplateEntity::getId, Function.identity()));
        var volumes = new TreeMap<String, BigDecimal>();
        for (var row : request.vehicles()) {
            var template = saved.get(row.templateId().intValue());
            if (template == null) throw new ApiException(404, "UNKNOWN_VEHICLE_TEMPLATE", "Шаблон техники не найден. Обновите список.");
            if (row.remainingLiters().compareTo(template.getTankCapacity()) > 0) {
                throw new InvalidRequestException("Остаток не может превышать ёмкость бака: " + template.getName());
            }
            var liters = template.getTankCapacity().subtract(row.remainingLiters()).multiply(BigDecimal.valueOf(row.count()));
            if (liters.signum() > 0) volumes.merge(row.fuelCode(), liters, BigDecimal::add);
        }
        var total = volumes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0 || total.compareTo(new BigDecimal("1000000")) > 0) {
            throw new InvalidRequestException("Общий объём должен быть больше нуля и не более 1 000 000 литров.");
        }
        var budgets = new ArrayList<EstimateResponseDTO>();
        for (var entry : volumes.entrySet()) {
            budgets.add(prices.estimate(new EstimateRequestDTO(request.sourceId(), request.regionId(), entry.getKey(),
                    request.priceDate(), request.comparisonRegionId(), entry.getValue(), null)));
        }
        var cost = budgets.stream().map(EstimateResponseDTO::estimatedCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otherCost = request.comparisonRegionId() == null ? null : budgets.stream()
                .map(budget -> budget.comparison().estimatedCost()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FleetEstimateResponseDTO(request.regionId(), request.priceDate(), total, cost, request.comparisonRegionId(),
                otherCost, otherCost == null ? null : cost.subtract(otherCost), List.copyOf(budgets));
    }
}
