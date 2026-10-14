package ru.krastti.service;

import lombok.RequiredArgsConstructor;

import ru.krastti.entity.dto.Source;
import ru.krastti.entity.CsvColumn;
import static ru.krastti.entity.CsvColumn.*;
import ru.krastti.exception.InvalidImportException;
import ru.krastti.exception.ImportJournalUnavailableException;

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
    private final JpaImportWriter writer;
    private final ImportFailureJournal failures;


    record Row(String region, String name, String fuel, LocalDate date, String frequency,
               BigDecimal price, String url) {}

    /**
     * Проверяет CSV и вызывает отдельную транзакцию записи.
     * Перехват снаружи proxy writer охватывает ошибки flush и commit.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int importFile(Path file, Source source) {
        var started = OffsetDateTime.now(ZoneOffset.UTC);
        int[] rejected = {0};
        try {
            validateSource(source);
            var rows = read(file, rejected);
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
    private static void validateSource(Source source) {
        require(source != null && source.id() != null && source.id().matches("[A-Za-z0-9_-]{1,64}"));
        require(source.name() != null && !source.name().isBlank() && source.name().length() <= 200);
        require(Set.of("OPEN_DATA", "DEMO", "BENZUP").contains(source.dataMode()));
        require(source.methodology() != null && !source.methodology().isBlank() && source.methodology().length() <= 2000);
    }

    private static List<Row> read(Path file, int[] rejected) throws Exception {
        require(Files.size(file) <= 10_000_000);
        var records = parseCsv(Files.readString(file, StandardCharsets.UTF_8));
        require(!records.isEmpty() && records.removeFirst().equals(CsvColumn.headers()));
        require(!records.isEmpty());
        var rows = new ArrayList<Row>();
        var keys = new HashSet<String>();
        var frequencies = new HashSet<String>();
        for (var fields : records) {
            try {
                require(fields.size() == CsvColumn.values().length && fields.stream().noneMatch(String::isBlank));
                require(REGION_CODE.read(fields).matches("[0-9]{1,16}") && REGION_NAME.read(fields).length() <= 200);
                require(Set.of("AI92", "DIESEL").contains(FUEL_CODE.read(fields)));
                var date = LocalDate.parse(OBSERVED_ON.read(fields));
                require(!date.isAfter(LocalDate.now(ZoneOffset.UTC)));
                require(Set.of("DAILY", "WEEKLY", "MONTHLY").contains(FREQUENCY.read(fields)));
                if (FREQUENCY.read(fields).equals("MONTHLY")) require(date.equals(date.withDayOfMonth(date.lengthOfMonth())));
                var price = new BigDecimal(PRICE.read(fields));
                require(price.signum() > 0 && price.scale() <= 6 && price.precision() - price.scale() <= 12);
                require(CURRENCY.read(fields).equals("RUB") && UNIT.read(fields).equals("LITER"));
                var uri = URI.create(SOURCE_URL.read(fields));
                require("https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null && SOURCE_URL.read(fields).length() <= 2000);
                require(keys.add(REGION_CODE.read(fields) + ":" + FUEL_CODE.read(fields) + ":" + date));
                frequencies.add(FREQUENCY.read(fields));
                rows.add(new Row(REGION_CODE.read(fields), REGION_NAME.read(fields), FUEL_CODE.read(fields), date, FREQUENCY.read(fields), price, SOURCE_URL.read(fields)));
            } catch (RuntimeException exception) { rejected[0]++; }
        }
        require(rejected[0] == 0 && frequencies.size() == 1);
        return rows;
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
