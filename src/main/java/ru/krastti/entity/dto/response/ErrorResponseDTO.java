package ru.krastti.entity.dto.response;

/**
 * Единое тело ответа API при ошибке обработки запроса.
 *
 * @param code код ошибки для программной обработки
 * @param message понятное пользователю сообщение без внутренних деталей и секретов
 */
public record ErrorResponseDTO(String code, String message) {}
