package ru.krastti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/**
 * Таблица {@code region} хранит справочник регионов, для которых загружаются цены.
 * Каждая запись содержит уникальный код региона и его название.
 * Связи используют числовой id, а уникальный код сохраняется для API и импорта.
 */
@Entity
@Table(name = "region")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 16, nullable = false, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    public RegionEntity(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
