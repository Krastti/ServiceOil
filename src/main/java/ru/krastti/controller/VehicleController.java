package ru.krastti.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.krastti.entity.dto.request.FleetEstimateRequestDTO;
import ru.krastti.entity.dto.request.VehicleTemplateRequestDTO;
import ru.krastti.entity.dto.response.FleetEstimateResponseDTO;
import ru.krastti.entity.dto.response.VehicleTemplateResponseDTO;
import ru.krastti.service.FleetEstimateService;
import ru.krastti.service.VehicleTemplateService;

/** Справочник техники и расчёт смешанного парка. Административный маршрут требует сессии и CSRF. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VehicleController {
    private final VehicleTemplateService templates;
    private final FleetEstimateService fleet;

    @GetMapping("/vehicle-templates")
    public ResponseEntity<List<VehicleTemplateResponseDTO>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(templates.list());
    }

    @PostMapping("/admin/vehicle-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleTemplateResponseDTO create(@Valid @RequestBody VehicleTemplateRequestDTO request) {
        return templates.create(request);
    }

    /** Заменяет название, ёмкость и топливо шаблона; требует сессии администратора и CSRF. */
    @PutMapping("/admin/vehicle-templates/{id}")
    public VehicleTemplateResponseDTO update(@PathVariable long id, @Valid @RequestBody VehicleTemplateRequestDTO request) {
        return templates.update(id, request);
    }

    /** Удаляет шаблон из справочника; неизвестный идентификатор возвращает 404. */
    @DeleteMapping("/admin/vehicle-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        templates.delete(id);
    }

    @PostMapping("/refueling/fleet-estimate")
    public FleetEstimateResponseDTO estimate(@Valid @RequestBody FleetEstimateRequestDTO request) {
        return fleet.estimate(request);
    }
}
