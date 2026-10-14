package ru.krastti.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionalPriceId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "source_id", length = 64, nullable = false)
    private String sourceId;

    @Column(name = "region_code", length = 16, nullable = false)
    private String regionCode;

    @Column(name = "fuel_code", length = 32, nullable = false)
    private String fuelCode;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    public RegionalPriceId(String sourceId, String regionCode, String fuelCode, LocalDate observedOn) {
        this.sourceId = java.util.Objects.requireNonNull(sourceId);
        this.regionCode = java.util.Objects.requireNonNull(regionCode);
        this.fuelCode = java.util.Objects.requireNonNull(fuelCode);
        this.observedOn = java.util.Objects.requireNonNull(observedOn);
    }
}
