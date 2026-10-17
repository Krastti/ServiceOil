package ru.krastti.entity.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/** Расчёт парка с несколькими видами топлива на одну точную дату наблюдения. */
public record FleetEstimateRequestDTO(String sourceId, @NotBlank String regionId,
        String comparisonRegionId, @NotNull LocalDate priceDate,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid FleetVehicleRequestDTO> vehicles) {}
