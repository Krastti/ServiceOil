package ru.krastti.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.krastti.entity.dto.*;
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

    @PostMapping("/refueling/fleet-estimate")
    public FleetEstimateResponseDTO estimate(@Valid @RequestBody FleetEstimateRequestDTO request) {
        return fleet.estimate(request);
    }
}
