package ru.krastti.entity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Row(String region, String name, String fuel, LocalDate date, String frequency,
                  BigDecimal price, String url) { }
