package ru.krastti.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.entity.VehicleTemplateEntity;
import ru.krastti.entity.dto.*;
import ru.krastti.repository.VehicleTemplateRepository;

/** Чтение и создание шаблонов техники; права на создание проверяет Spring Security. */
@Service
@RequiredArgsConstructor
public class VehicleTemplateService {
    private final VehicleTemplateRepository templates;

    @Transactional(readOnly = true)
    public List<VehicleTemplateResponseDTO> list() {
        return templates.findAllByOrderByNameAscIdAsc().stream().map(VehicleTemplateService::dto).toList();
    }

    @Transactional
    public VehicleTemplateResponseDTO create(VehicleTemplateRequestDTO request) {
        return dto(templates.save(new VehicleTemplateEntity(request.name().strip(), request.tankCapacity(), request.fuelCode())));
    }

    private static VehicleTemplateResponseDTO dto(VehicleTemplateEntity template) {
        return new VehicleTemplateResponseDTO(template.getId(), template.getName(), template.getTankCapacity(), template.getFuelCode());
    }
}
