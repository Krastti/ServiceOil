package ru.krastti.entity.dto;

/**
 * Описание источника, передаваемое сервису импорта CSV.
 * Используется для проверки и сохранения происхождения загружаемого набора.
 *
 * @param id идентификатор источника
 * @param name название источника
 * @param dataMode режим данных: OPEN_DATA или DEMO
 * @param methodology описание методики формирования данных
 */
public record Source(String id, String name, String dataMode, String methodology) {}
