package ru.krastti.entity.dto.response;

import ru.krastti.entity.dto.Availability;
import ru.krastti.entity.dto.Fuel;
import ru.krastti.entity.dto.Metadata;
import ru.krastti.entity.dto.Region;

import java.util.List;

/**
 * Ответ каталога доступных регионов, видов топлива и сочетаний для одного источника.
 *
 * @param metadata сведения об источнике и ограничения данных
 * @param regions регионы, для которых у источника есть цены
 * @param fuels виды топлива, для которых у источника есть цены
 * @param availability границы наблюдений для каждого доступного сочетания региона и топлива
 */
public record CatalogResponseDTO(Metadata metadata, List<Region> regions, List<Fuel> fuels, List<Availability> availability) {}
