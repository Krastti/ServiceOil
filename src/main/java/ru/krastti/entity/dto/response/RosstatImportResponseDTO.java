package ru.krastti.entity.dto.response;

/** Источник недельных данных, число найденных страниц и число обработанных строк (включая upsert). */
public record RosstatImportResponseDTO(String sourceId, int publicationsFound, int rowsImported) {}
