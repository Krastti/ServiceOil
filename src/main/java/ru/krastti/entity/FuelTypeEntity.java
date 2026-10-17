package ru.krastti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/**
 * Таблица {@code fuel_type} хранит справочник видов топлива.
 * Каждая запись содержит уникальный код топлива и его название,
 * например {@code DIESEL} — дизельное топливо или {@code AI92} — АИ-92.
 * Связи используют числовой id, код остаётся уникальным обозначением топлива.
 */
@Entity
@Table(name = "fuel_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FuelTypeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 32, nullable = false, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;
}
