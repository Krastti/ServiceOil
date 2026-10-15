package ru.krastti.parser;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Разбирает недельную HTML-публикацию Росстата с ценами по регионам ПФО. */
@SuppressWarnings({"unused", "SpellCheckingInspection"})
public final class RosstatWeeklyPriceParser {
    private static final int MAX_HTML_LENGTH = 10_000_000;
    private static final Pattern PRICE = Pattern.compile("\\d+(?:[,.]\\d+)?");
    private static final Pattern DATE = Pattern.compile(
            "(?iu)Средние\\s+потребительские\\s+цены\\s+на\\s+бензин\\s+автомобильный\\s+и\\s+дизельное\\s+топливо"
                    + "\\s+на\\s+(\\d{1,2})\\s+([\\p{L}]+)\\s+(\\d{4})\\s+(?:года|г\\.)");
    private static final Map<String, Region> PFO_REGIONS = pfoRegions();

    /** Нормализованная цена, готовая к сериализации во внутренний CSV ServiceOil. */
    public record PriceRow(
            String regionCode,
            String regionName,
            String fuelCode,
            LocalDate observedOn,
            BigDecimal price,
            String sourceUrl) {}

    /** Извлекает цены АИ-92 и дизельного топлива для распознанных субъектов ПФО. */
    public List<PriceRow> parse(String html, URI sourceUri) {
        require(html != null && !html.isBlank() && html.length() <= MAX_HTML_LENGTH);
        validateSourceUri(sourceUri);

        var document = readHtml(html);
        var observedOn = readObservedOn(document.text());
        var table = findPriceTable(document.tables());
        var result = new LinkedHashMap<String, PriceRow>();

        for (var cells : table.rows()) {
            if (cells.size() < 2) continue;
            var region = PFO_REGIONS.get(normalize(cells.getFirst()));
            if (region == null) continue;

            var values = new ArrayList<BigDecimal>();
            for (int index = 1; index < cells.size(); index++) {
                var value = cells.get(index).replace("\u00a0", "").replace(" ", "");
                var matcher = PRICE.matcher(value);
                if (matcher.matches()) values.add(new BigDecimal(matcher.group().replace(',', '.')));
            }
            // В таблице Росстата идут: бензин всего, АИ-92, АИ-95, АИ-98+, дизель.
            require(values.size() == 5);
            add(result, region, "AI92", observedOn, values.get(1), sourceUri);
            add(result, region, "DIESEL", observedOn, values.get(4), sourceUri);
        }

        require(!result.isEmpty());
        return List.copyOf(result.values());
    }

    /** Сериализует результат в CSV, который принимает CsvImportService. */
    public String toCsv(List<PriceRow> rows) {
        require(rows != null && !rows.isEmpty());
        var csv = new StringBuilder(
                "region_code,region_name,fuel_code,observed_on,frequency,price,currency,unit,source_url\n");
        for (var row : rows) {
            csv.append(row.regionCode()).append(',')
                    .append(csvField(row.regionName())).append(',')
                    .append(row.fuelCode()).append(',')
                    .append(row.observedOn()).append(",WEEKLY,")
                    .append(row.price().toPlainString()).append(",RUB,LITER,")
                    .append(csvField(row.sourceUrl())).append('\n');
        }
        return csv.toString();
    }

    private static void add(Map<String, PriceRow> result, Region region, String fuelCode,
                            LocalDate observedOn, BigDecimal price, URI sourceUri) {
        require(price.signum() > 0 && price.scale() <= 6);
        var row = new PriceRow(region.code(), region.name(), fuelCode, observedOn, price, sourceUri.toString());
        var key = region.code() + ':' + fuelCode;
        require(result.putIfAbsent(key, row) == null);
    }

    private static HtmlDocument readHtml(String html) {
        var callback = new TableCollector();
        try {
            new ParserDelegator().parse(new StringReader(html), callback, true);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Не удалось разобрать HTML Росстата", exception);
        }
        return new HtmlDocument(callback.text.toString(), List.copyOf(callback.tables));
    }

    private static LocalDate readObservedOn(String text) {
        var matcher = DATE.matcher(normalize(text));
        require(matcher.find());
        var day = Integer.parseInt(matcher.group(1));
        var month = russianMonth(matcher.group(2));
        var year = Integer.parseInt(matcher.group(3));
        return LocalDate.of(year, month, day);
    }

    private static Month russianMonth(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "января" -> Month.JANUARY;
            case "февраля" -> Month.FEBRUARY;
            case "марта" -> Month.MARCH;
            case "апреля" -> Month.APRIL;
            case "мая" -> Month.MAY;
            case "июня" -> Month.JUNE;
            case "июля" -> Month.JULY;
            case "августа" -> Month.AUGUST;
            case "сентября" -> Month.SEPTEMBER;
            case "октября" -> Month.OCTOBER;
            case "ноября" -> Month.NOVEMBER;
            case "декабря" -> Month.DECEMBER;
            default -> throw new IllegalArgumentException("Неизвестный месяц в публикации Росстата");
        };
    }

    private static Table findPriceTable(List<Table> tables) {
        for (var table : tables) {
            var beginning = table.rows().stream().limit(8).flatMap(List::stream)
                    .map(RosstatWeeklyPriceParser::normalize).map(value -> value.toLowerCase(Locale.ROOT))
                    .reduce("", (left, right) -> left + ' ' + right);
            boolean hasPfoRegion = table.rows().stream().anyMatch(row -> !row.isEmpty()
                    && PFO_REGIONS.containsKey(normalize(row.getFirst())));
            if (beginning.contains("аи-92") && beginning.contains("дизельное топливо") && hasPfoRegion) {
                return table;
            }
        }
        throw new IllegalArgumentException("В публикации Росстата не найдена таблица региональных цен");
    }

    private static void validateSourceUri(URI uri) {
        require(uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null);
        var host = uri.getHost().toLowerCase(Locale.ROOT);
        require(host.equals("rosstat.gov.ru") || host.endsWith(".rosstat.gov.ru"));
    }

    private static String normalize(String value) {
        return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String csvField(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static Map<String, Region> pfoRegions() {
        var regions = new LinkedHashMap<String, Region>();
        addRegion(regions, "02", "Республика Башкортостан", "Республика Башкортостан");
        addRegion(regions, "12", "Республика Марий Эл", "Республика Марий Эл");
        addRegion(regions, "13", "Республика Мордовия", "Республика Мордовия");
        addRegion(regions, "16", "Республика Татарстан", "Республика Татарстан", "Республика Татарстан (Татарстан)");
        addRegion(regions, "18", "Удмуртская Республика", "Удмуртская Республика", "Удмуртия");
        addRegion(regions, "21", "Чувашская Республика", "Чувашская Республика", "Чувашская Республика - Чувашия");
        addRegion(regions, "43", "Кировская область", "Кировская область");
        addRegion(regions, "52", "Нижегородская область", "Нижегородская область");
        addRegion(regions, "56", "Оренбургская область", "Оренбургская область");
        addRegion(regions, "58", "Пензенская область", "Пензенская область");
        addRegion(regions, "59", "Пермский край", "Пермский край");
        addRegion(regions, "63", "Самарская область", "Самарская область");
        addRegion(regions, "64", "Саратовская область", "Саратовская область");
        addRegion(regions, "73", "Ульяновская область", "Ульяновская область");
        return Map.copyOf(regions);
    }

    private static void addRegion(Map<String, Region> regions, String code, String name, String... aliases) {
        for (var alias : aliases) regions.put(normalize(alias), new Region(code, name));
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Публикация Росстата не соответствует ожидаемому формату");
    }

    private record Region(String code, String name) {}
    private record Table(List<List<String>> rows) {}
    private record HtmlDocument(String text, List<Table> tables) {}

    private static final class TableCollector extends HTMLEditorKit.ParserCallback {
        private final StringBuilder text = new StringBuilder();
        private final List<Table> tables = new ArrayList<>();
        private List<List<String>> tableRows;
        private List<String> row;
        private StringBuilder cell;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (tag == HTML.Tag.TABLE) tableRows = new ArrayList<>();
            else if (tag == HTML.Tag.TR && tableRows != null) row = new ArrayList<>();
            else if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && row != null) cell = new StringBuilder();
            else if (tag == HTML.Tag.BR && cell != null) cell.append(' ');
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (tag == HTML.Tag.BR && cell != null) cell.append(' ');
        }

        @Override
        public void handleText(char[] data, int position) {
            text.append(data).append(' ');
            if (cell != null) cell.append(data);
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && cell != null && row != null) {
                row.add(normalize(cell.toString()));
                cell = null;
            } else if (tag == HTML.Tag.TR && row != null && tableRows != null) {
                tableRows.add(List.copyOf(row));
                row = null;
            } else if (tag == HTML.Tag.TABLE && tableRows != null) {
                tables.add(new Table(List.copyOf(tableRows)));
                tableRows = null;
            }
        }
    }
}
