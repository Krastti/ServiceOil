package ru.krastti.entity.dto.response;

/** Состояние входа и CSRF-токен; username равен null до входа, пароль и его хеш не передаются. */
public record AdminSessionResponseDTO(boolean authenticated, String username, String csrfToken,
                                     String csrfHeader, String csrfParameter) {}
