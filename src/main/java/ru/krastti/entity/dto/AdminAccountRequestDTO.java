package ru.krastti.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Смена реквизитов своей учётной записи; новый пароль необязателен, текущий обязателен. */
public record AdminAccountRequestDTO(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotEmpty @Size(max = 256) String currentPassword,
        @Size(min = 8, max = 72) String newPassword) {}
