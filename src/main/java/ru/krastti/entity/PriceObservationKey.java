package ru.krastti.entity;

import java.time.LocalDate;

/** Бизнес-ключ сопоставления строк импорта; не является первичным ключом JPA. */
public record PriceObservationKey(String sourceCode, String regionCode, String fuelCode, LocalDate observedOn) {
    public static PriceObservationKey of(RegionalPriceEntity price) {
        return new PriceObservationKey(price.getSource().getCode(), price.getRegion().getCode(),
                price.getFuel().getCode(), price.getObservedOn());
    }
}
