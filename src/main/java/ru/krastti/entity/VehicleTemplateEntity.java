package ru.krastti.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Сохранённый администратором шаблон техники с ёмкостью бака и топливом по умолчанию. */
@Entity
@Table(name = "vehicle_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleTemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "tank_capacity", nullable = false, precision = 12, scale = 2)
    private BigDecimal tankCapacity;
    @Column(name = "fuel_code", nullable = false, length = 32)
    private String fuelCode;

    public VehicleTemplateEntity(String name, BigDecimal tankCapacity, String fuelCode) {
        this.name = name;
        this.tankCapacity = tankCapacity;
        this.fuelCode = fuelCode;
    }
}
