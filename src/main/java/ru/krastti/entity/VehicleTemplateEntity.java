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
    private Integer id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "tank_capacity", nullable = false, precision = 12, scale = 2)
    private BigDecimal tankCapacity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelTypeEntity fuel;

    public VehicleTemplateEntity(String name, BigDecimal tankCapacity, FuelTypeEntity fuel) {
        this.name = name;
        this.tankCapacity = tankCapacity;
        this.fuel = fuel;
    }
}
