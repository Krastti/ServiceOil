package ru.krastti.entity.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Сведения об источнике, методике и ограничениях цен, включаемые в ответы API.
 *
 * @param source идентификатор источника
 * @param sourceName название источника
 * @param dataMode режим данных: OPEN_DATA или DEMO
 * @param unit единица объёма; сейчас LITER
 * @param currency валюта цены; сейчас RUB
 * @param frequency периодичность наблюдений; null, если у источника нет цен
 * @param methodology описание методики формирования данных
 * @param lastImportedAt время последнего успешного импорта; null, если оно не задано
 * @param warnings предупреждения о демонстрационных, архивных данных или последнем сбое импорта
 */
public record Metadata(String source, String sourceName, String dataMode, String unit, String currency,
                       String frequency, String methodology, OffsetDateTime lastImportedAt, List<String> warnings) {}
