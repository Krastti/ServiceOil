package ru.krastti.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.entity.VehicleTemplateEntity;
import ru.krastti.entity.dto.request.VehicleTemplateRequestDTO;
import ru.krastti.entity.dto.response.VehicleTemplateResponseDTO;
import ru.krastti.repository.VehicleTemplateRepository;
import ru.krastti.repository.FuelTypeRepository;
import ru.krastti.exception.InvalidRequestException;

/** Чтение и создание шаблонов техники; права на создание проверяет Spring Security. */
@Service
@RequiredArgsConstructor
public class VehicleTemplateService {
    private final VehicleTemplateRepository templates;
    private final FuelTypeRepository fuels;

    @Transactional(readOnly = true)
    public List<VehicleTemplateResponseDTO> list() {
        return templates.findAllByOrderByNameAscIdAsc().stream().map(VehicleTemplateService::dto).toList();
    }

    @Transactional
    public VehicleTemplateResponseDTO create(VehicleTemplateRequestDTO request) {
        var fuel = fuels.findByCode(request.fuelCode())
                .orElseThrow(() -> new InvalidRequestException("Неизвестный вид топлива."));
        return dto(templates.save(new VehicleTemplateEntity(request.name().strip(), request.tankCapacity(), fuel)));
    }

    private static VehicleTemplateResponseDTO dto(VehicleTemplateEntity template) {
        return new VehicleTemplateResponseDTO(template.getId().longValue(), template.getName(), template.getTankCapacity(), template.getFuel().getCode());
    }
}
