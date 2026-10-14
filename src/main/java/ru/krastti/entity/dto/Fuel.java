package ru.krastti.entity.dto;

/**
 * Вид топлива, доступный в каталоге выбранного источника.
 *
 * @param code код топлива, например DIESEL или AI92
 * @param name название топлива для отображения пользователю
 */
public record Fuel(String code, String name) {}
