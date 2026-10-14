package ru.krastti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/**
 * Таблица {@code fuel_type} хранит справочник видов топлива.
 * Каждая запись содержит уникальный код топлива и его название,
 * например {@code DIESEL} — дизельное топливо или {@code AI92} — АИ-92.
 * Код используется для привязки цен в таблице {@code regional_price} к виду топлива.
 */
@Entity
@Table(name = "fuel_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FuelTypeEntity {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;
}
