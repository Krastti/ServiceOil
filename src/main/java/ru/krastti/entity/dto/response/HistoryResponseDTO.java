package ru.krastti.entity.dto.response;

import ru.krastti.entity.dto.Metadata;
import ru.krastti.entity.dto.PricePoint;

import java.time.LocalDate;
import java.util.List;

/**
 * Ответ с опубликованной историей цены для одного региона и вида топлива.
 * Даты без наблюдений не дополняются вымышленными ценами.
 *
 * @param metadata сведения об источнике и ограничения данных
 * @param regionId код региона
 * @param fuelCode код вида топлива
 * @param from начало запрошенного периода включительно
 * @param to конец запрошенного периода включительно
 * @param points наблюдения по возрастанию даты; пустой список, если в периоде нет цен
 */
public record HistoryResponseDTO(Metadata metadata, String regionId, String fuelCode, LocalDate from, LocalDate to, List<PricePoint> points) {}
