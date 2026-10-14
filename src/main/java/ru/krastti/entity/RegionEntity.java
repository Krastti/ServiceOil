package ru.krastti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/**
 * Таблица {@code region} хранит справочник регионов, для которых загружаются цены.
 * Каждая запись содержит уникальный код региона и его название.
 * Код используется для привязки цен в таблице {@code regional_price} к региону.
 */
@Entity
@Table(name = "region")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionEntity {

    @Id
    @Column(length = 16)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    public RegionEntity(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
