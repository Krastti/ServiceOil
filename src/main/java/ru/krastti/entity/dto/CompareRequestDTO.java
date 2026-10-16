package ru.krastti.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** JSON-запрос сравнения двух регионов за включительный период. */
public record CompareRequestDTO(String sourceId, @NotBlank String regionA,
                               @NotBlank String regionB, @NotBlank String fuelCode,
                               @NotNull LocalDate from, @NotNull LocalDate to) {
    public CompareRequestDTO {
        if (sourceId == null) sourceId = "ROSSTAT_PFO_WEEKLY";
    }
}
