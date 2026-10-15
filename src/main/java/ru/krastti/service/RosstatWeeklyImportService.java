package ru.krastti.service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.krastti.entity.dto.Source;
import ru.krastti.exception.InvalidRequestException;
import ru.krastti.parser.RosstatWeeklyPriceParser;

/** Получает публикации Росстата, разбирает их и передаёт строки в общий импорт CSV. */
@Service
public class RosstatWeeklyImportService {
    private static final int RECENT_PUBLICATION_DAYS = 7;
    private static final Pattern HREF = Pattern.compile("(?is)\\bhref\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern PUBLISHED_ON = Pattern.compile("_(\\d{2}-\\d{2}-\\d{4})\\.html$");
    private static final DateTimeFormatter PUBLISHED_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-uuuu");
    private static final String SOURCE_ID = "ROSSTAT_PFO_WEEKLY";
    private static final Source SOURCE = new Source(
            SOURCE_ID,
            "Росстат ПФО (недельные цены)",
            "OPEN_DATA",
            "Средние потребительские цены на бензин автомобильный и дизельное топливо по субъектам ПФО; "
                    + "еженедельный мониторинг Росстата, дата регистрации указана в публикации; рубли за литр.");

    private final RestClient http;
    private final RosstatWeeklyPriceParser parser;
    private final CsvImportService importer;
    private final URI publicationIndex;
    private final Clock clock;

    // TODO Вынести url Росстата в application.context, чтобы избавиться от hard code
    @Autowired
    public RosstatWeeklyImportService(
            CsvImportService importer,
            @Value("${rosstat.weekly.index-url:https://rosstat.gov.ru/compendium/document/50798}") URI publicationIndex) {
        this(RestClient.builder(), new RosstatWeeklyPriceParser(), importer, publicationIndex, Clock.systemUTC());
    }

    RosstatWeeklyImportService(
            RestClient.Builder http,
            RosstatWeeklyPriceParser parser,
            CsvImportService importer,
            URI publicationIndex,
            Clock clock) {
        this.http = http.build();
        this.parser = parser;
        this.importer = importer;
        this.publicationIndex = publicationIndex;
        this.clock = clock;
    }

    /** Импортирует публикации, наблюдения в которых попадают в заданный включительный период. */
    public ImportResult importRange(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return importRange(from, to, to.plusDays(7));
    }

    private ImportResult importRange(LocalDate from, LocalDate to, LocalDate publishedTo) {
        var publicationUris = findPublicationUris(from, publishedTo);
        return importPublications(publicationUris, from, to);
    }

    /** Повторно проверяет двухнедельное окно, чтобы подобрать публикации после пропущенного запуска. */
    public ImportResult importNewPublications() {
        var to = LocalDate.now(clock);
        var from = to.minusDays(RECENT_PUBLICATION_DAYS);
        return importRange(from, to, to);
    }

    private ImportResult importPublications(List<URI> publicationUris, LocalDate from, LocalDate to) {
        var rowsByKey = new LinkedHashMap<String, RosstatWeeklyPriceParser.PriceRow>();
        for (var uri : publicationUris) {
            var html = http.get().uri(uri).retrieve().body(String.class);
            if (html == null || html.length() > 10_000_000) {
                throw new IllegalArgumentException("Публикация Росстата пуста или слишком велика");
            }
            if (!containsFuelPricePublication(html)) continue;
            for (var row : parser.parse(html, uri)) {
                if (row.observedOn().isBefore(from) || row.observedOn().isAfter(to)) continue;
                var key = row.regionCode() + ':' + row.fuelCode() + ':' + row.observedOn();
                rowsByKey.put(key, row);
            }
        }
        if (rowsByKey.isEmpty()) return new ImportResult(publicationUris.size(), 0);

        var rows = List.copyOf(rowsByKey.values());
        int imported = importer.importCsv(parser.toCsv(rows), SOURCE);
        return new ImportResult(publicationUris.size(), imported);
    }

    private List<URI> findPublicationUris(LocalDate publishedFrom, LocalDate publishedTo) {
        requireRosstatUri(publicationIndex);
        var html = http.get().uri(publicationIndex).retrieve().body(String.class);
        if (html == null || html.length() > 10_000_000) {
            throw new IllegalArgumentException("Список публикаций Росстата пуст или слишком велик");
        }

        var result = new ArrayList<URI>();
        Matcher matcher = HREF.matcher(html);
        while (matcher.find()) {
            URI candidate;
            try {
                candidate = publicationIndex.resolve(URI.create(matcher.group(1)));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            if (!isRosstatUri(candidate) || !candidate.getPath().startsWith("/storage/mediabank/")) continue;
            var dateMatcher = PUBLISHED_ON.matcher(candidate.getPath());
            if (!dateMatcher.find()) continue;
            try {
                var publishedOn = LocalDate.parse(dateMatcher.group(1), PUBLISHED_DATE_FORMAT);
                if (!publishedOn.isBefore(publishedFrom) && !publishedOn.isAfter(publishedTo)) result.add(candidate);
            } catch (DateTimeParseException ignored) {
                // Игнорируем ссылки с неподходящим именем файла.
            }
        }
        return result.stream().distinct().toList();
    }

    private static boolean containsFuelPricePublication(String html) {
        var normalized = html.toLowerCase(Locale.ROOT).replace('ё', 'е');
        return normalized.contains("средние потребительские цены на бензин автомобильный")
                && normalized.contains("дизельное топливо");
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new InvalidRequestException("Выберите период от 1 до 366 дней: начало не позже конца");
        }
    }

    private static void requireRosstatUri(URI uri) {
        if (!isRosstatUri(uri)) throw new IllegalArgumentException("Разрешены только HTTPS-адреса Росстата");
    }

    private static boolean isRosstatUri(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null) return false;
        var host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("rosstat.gov.ru") || host.endsWith(".rosstat.gov.ru");
    }

    public record ImportResult(int publicationsFound, int rowsImported) {}
}
