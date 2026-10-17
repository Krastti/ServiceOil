package ru.krastti.entity.dto.request;

/** JSON-запрос каталога; отсутствующий sourceId означает ROSSTAT_PFO_WEEKLY. */
public record CatalogRequestDTO(String sourceId) {
    public CatalogRequestDTO {
        if (sourceId == null) sourceId = "ROSSTAT_PFO_WEEKLY";
    }
}
