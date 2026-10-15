package ru.krastti.service;

import lombok.RequiredArgsConstructor;

import ru.krastti.entity.dto.Source;
import ru.krastti.entity.CsvColumn;
import static ru.krastti.entity.CsvColumn.*;
import ru.krastti.exception.InvalidImportException;
import ru.krastti.exception.ImportJournalUnavailableException;
import ru.krastti.entity.dto.Row;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CsvImportService {
    private final PriceImportService writer;
    private final ImportFailureJournal failures;

    /**
     * Проверяет CSV и вызывает отдельную транзакцию записи.
     * Перехват снаружи proxy writer охватывает ошибки flush и commit.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int importFile(Path file, Source source) {
        return performImport(source, () -> Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Импортирует уже сформированный CSV через ту же проверку и транзакционную запись, что и файл. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int importCsv(String csv, Source source) {
        return performImport(source, () -> csv);
    }

    /**
     * Проверяет источник и CSV, затем передает строки на запись.
     * При сбое фиксирует попытку в журнале отдельной транзакцией и выбрасывает ошибку импорта.
     */
    private int performImport(Source source, CsvContent content) {
        var started = OffsetDateTime.now(ZoneOffset.UTC);
        int[] rejected = {0};
        try {
            validateSource(source);
            var rows = read(content.read(), rejected);
            writer.write(source, rows, started);
            return rows.size();
        } catch (Exception exception) {
            if (source != null && source.id() != null && source.id().matches("[A-Za-z0-9_-]{1,64}")) {
                try {
                    failures.record(source.id(), started, rejected[0]);
                } catch (RuntimeException journalFailure) {
                    throw new ImportJournalUnavailableException();
                }
            }
            throw new InvalidImportException();
        }
    }

    @FunctionalInterface
    private interface CsvContent {
        String read() throws Exception;
    }

    /** Проверяет обязательные поля источника перед импортом. */
    private static void validateSource(Source source) {
        require(source != null);
        require(source.id() != null && source.id().matches("[A-Za-z0-9_-]{1,64}"));
        require(hasTextWithinLimit(source.name(), 200));
        require(Set.of("OPEN_DATA", "DEMO").contains(source.dataMode()));
        require(hasTextWithinLimit(source.methodology(), 2000));
    }

    /** Проверяет, что строка задана, не пустая и не превышает лимит длины. */
    private static boolean hasTextWithinLimit(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    /** Проверяет структуру CSV и преобразует записи в DTO строк цены. */
    private static List<Row> read(String csv, int[] rejected) {
        require(csv != null && csv.getBytes(StandardCharsets.UTF_8).length <= 10_000_000);
        var records = parseCsv(csv);
        require(!records.isEmpty() && records.removeFirst().equals(CsvColumn.headers()));
        require(!records.isEmpty());

        var rows = new ArrayList<Row>();
        var keys = new HashSet<String>();
        for (var fields : records) {
            try {
                rows.add(readRow(fields, keys));
            } catch (RuntimeException exception) {
                rejected[0]++;
            }
        }
        require(rejected[0] == 0);
        return rows;
    }

    /** Проверяет одну CSV-запись и создает DTO строки цены. */
    private static Row readRow(List<String> fields, Set<String> keys) {
        require(fields.size() == CsvColumn.values().length && fields.stream().noneMatch(String::isBlank));

        var regionCode = REGION_CODE.read(fields);
        var regionName = REGION_NAME.read(fields);
        var fuelCode = FUEL_CODE.read(fields);
        var observedOn = LocalDate.parse(OBSERVED_ON.read(fields));
        var frequency = FREQUENCY.read(fields);
        var price = new BigDecimal(PRICE.read(fields));
        var currency = CURRENCY.read(fields);
        var unit = UNIT.read(fields);
        var sourceUrl = SOURCE_URL.read(fields);

        require(regionCode.matches("[0-9]{1,16}") && regionName.length() <= 200);
        require(Set.of("AI92", "DIESEL").contains(fuelCode));
        require(!observedOn.isAfter(LocalDate.now(ZoneOffset.UTC)));
        require(frequency.equals("WEEKLY"));
        require(price.signum() > 0 && price.scale() <= 6 && price.precision() - price.scale() <= 12);
        require(currency.equals("RUB") && unit.equals("LITER"));

        var uri = URI.create(sourceUrl);
        require("https".equals(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && sourceUrl.length() <= 2000);
        require(keys.add(regionCode + ":" + fuelCode + ":" + observedOn));

        return new Row(regionCode, regionName, fuelCode, observedOn, frequency, price, sourceUrl);
    }

    static List<List<String>> parseCsv(String input) {
        if (input.startsWith("\uFEFF")) input = input.substring(1);
        var records = new ArrayList<List<String>>();
        var fields = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else field.append(c);
            } else if (c == '"') {
                require(field.isEmpty() && !closed); quoted = true;
            } else if (c == ',' || c == '\n' || c == '\r') {
                fields.add(field.toString()); field.setLength(0); closed = false;
                if (c != ',') {
                    if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
                    records.add(fields); fields = new ArrayList<>();
                }
            } else { require(!closed); field.append(c); }
        }
        require(!quoted);
        if (!field.isEmpty() || !fields.isEmpty() || closed) { fields.add(field.toString()); records.add(fields); }
        return records;
    }

    private static void require(boolean condition) { if (!condition) throw new InvalidImportException(); }
}
