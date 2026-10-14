package ru.krastti.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** JSON-фильтры истории или сводки; границы периода включительны. */
public record PricePeriodRequestDTO(String sourceId, @NotBlank String regionId,
                                   @NotBlank String fuelCode, @NotNull LocalDate from,
                                   @NotNull LocalDate to) {
    public PricePeriodRequestDTO {
        if (sourceId == null) sourceId = "ROSSTAT_PFO";
    }
}
