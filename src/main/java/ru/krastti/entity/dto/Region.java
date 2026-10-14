package ru.krastti.entity.dto;

/**
 * Регион, для которого выбранный источник содержит цены.
 *
 * @param regionId уникальный код региона
 * @param name название региона для отображения пользователю
 */
public record Region(String regionId, String name) {}
