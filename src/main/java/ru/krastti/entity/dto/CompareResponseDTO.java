package ru.krastti.entity.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Ответ сравнения истории цен двух регионов в пределах одного источника.
 * Числовое сравнение выполняется на последней общей дате наблюдения.
 *
 * @param metadata сведения об общем источнике и методике
 * @param regionA код первого региона
 * @param regionB код второго региона
 * @param fuelCode код вида топлива
 * @param from начало запрошенного периода включительно
 * @param to конец запрошенного периода включительно
 * @param historyA опубликованные цены первого региона по возрастанию даты
 * @param historyB опубликованные цены второго региона по возрастанию даты
 * @param commonDates общие даты наблюдений по возрастанию
 * @param comparison сравнение на последней общей дате; null, если общих дат нет
 * @param warnings предупреждения, относящиеся к сравнению
 */
public record CompareResponseDTO(Metadata metadata, String regionA, String regionB, String fuelCode, LocalDate from, LocalDate to,
                      List<PricePoint> historyA, List<PricePoint> historyB, List<LocalDate> commonDates,
                      Comparison comparison, List<String> warnings) {}
