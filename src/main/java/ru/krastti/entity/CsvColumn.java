package ru.krastti.entity;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Колонки внутреннего CSV ServiceOil в обязательном порядке, включая проверку заголовка. */
@Getter
@RequiredArgsConstructor
public enum CsvColumn {
    REGION_CODE("region_code"),
    REGION_NAME("region_name"),
    FUEL_CODE("fuel_code"),
    OBSERVED_ON("observed_on"),
    FREQUENCY("frequency"),
    PRICE("price"),
    CURRENCY("currency"),
    UNIT("unit"),
    SOURCE_URL("source_url");

    private final String header;

    public String read(List<String> row) { return row.get(ordinal()); }

    public static List<String> headers() {
        return Arrays.stream(values()).map(CsvColumn::getHeader).toList();
    }
}
