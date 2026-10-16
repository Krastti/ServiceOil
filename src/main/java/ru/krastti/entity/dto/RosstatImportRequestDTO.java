package ru.krastti.entity.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Включительные границы наблюдений; обе даты обязательны, период не длиннее 366 дней. */
public record RosstatImportRequestDTO(@NotNull LocalDate from, @NotNull LocalDate to) {}
