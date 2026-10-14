package ru.krastti.entity.dto;

import java.util.List;

/**
 * Ответ каталога доступных регионов, видов топлива и сочетаний для одного источника.
 *
 * @param metadata сведения об источнике и ограничения данных
 * @param regions регионы, для которых у источника есть цены
 * @param fuels виды топлива, для которых у источника есть цены
 * @param availability границы наблюдений для каждого доступного сочетания региона и топлива
 */
public record Catalog(Metadata metadata, List<Region> regions, List<Fuel> fuels, List<Availability> availability) {}
