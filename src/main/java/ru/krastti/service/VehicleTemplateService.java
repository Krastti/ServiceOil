package ru.krastti.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.entity.VehicleTemplateEntity;
import ru.krastti.entity.dto.request.VehicleTemplateRequestDTO;
import ru.krastti.entity.dto.response.VehicleTemplateResponseDTO;
import ru.krastti.repository.VehicleTemplateRepository;
import ru.krastti.repository.FuelTypeRepository;
import ru.krastti.exception.InvalidRequestException;
import ru.krastti.exception.ApiException;
import ru.krastti.logging.AuditAction;
import ru.krastti.logging.LogOperation;

/** Справочник шаблонов техники; изменения доступны только администратору. */
@Service
@RequiredArgsConstructor
public class VehicleTemplateService {
    private final VehicleTemplateRepository templates;
    private final FuelTypeRepository fuels;

    @Transactional(readOnly = true)
    @Cacheable("vehicle-templates")
    @LogOperation(value = "vehicle-templates.list", warnIfSlow = true)
    public List<VehicleTemplateResponseDTO> list() {
        return templates.findAllByOrderByNameAscIdAsc().stream().map(VehicleTemplateService::dto).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @AuditAction(AuditAction.Action.VEHICLE_TEMPLATE_CREATED)
    @CacheEvict(value = "vehicle-templates", allEntries = true)
    public VehicleTemplateResponseDTO create(VehicleTemplateRequestDTO request) {
        var fuel = fuels.findByCode(request.fuelCode())
                .orElseThrow(() -> new InvalidRequestException("Неизвестный вид топлива."));
        return dto(templates.save(new VehicleTemplateEntity(request.name().strip(), request.tankCapacity(), fuel)));
    }

    /** Сохраняет новые параметры существующего шаблона и сбрасывает кеш справочника. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @AuditAction(AuditAction.Action.VEHICLE_TEMPLATE_UPDATED)
    @CacheEvict(value = "vehicle-templates", allEntries = true)
    public VehicleTemplateResponseDTO update(long id, VehicleTemplateRequestDTO request) {
        var template = requireTemplate(id);
        var fuel = fuels.findByCode(request.fuelCode())
                .orElseThrow(() -> new InvalidRequestException("Неизвестный вид топлива."));
        template.update(request.name().strip(), request.tankCapacity(), fuel);
        return dto(templates.save(template));
    }

    /** Удаляет шаблон; неизвестный идентификатор возвращает ту же ошибку, что и расчёт парка. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @AuditAction(AuditAction.Action.VEHICLE_TEMPLATE_DELETED)
    @CacheEvict(value = "vehicle-templates", allEntries = true)
    public void delete(long id) {
        templates.delete(requireTemplate(id));
    }

    private VehicleTemplateEntity requireTemplate(long id) {
        if (id > 0 && id <= Integer.MAX_VALUE) {
            return templates.findById((int) id).orElseThrow(VehicleTemplateService::unknownTemplate);
        }
        throw unknownTemplate();
    }

    private static ApiException unknownTemplate() {
        return new ApiException(404, "UNKNOWN_VEHICLE_TEMPLATE", "Шаблон техники не найден. Обновите список.");
    }

    private static VehicleTemplateResponseDTO dto(VehicleTemplateEntity template) {
        return new VehicleTemplateResponseDTO(template.getId().longValue(), template.getName(), template.getTankCapacity(), template.getFuel().getCode());
    }
}
