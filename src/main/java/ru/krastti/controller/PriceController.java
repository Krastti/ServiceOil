package ru.krastti.controller;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.krastti.entity.dto.*;
import ru.krastti.service.PriceService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PriceController {
    private final PriceService service;
    
    @PostMapping("/catalog")
    public CatalogResponseDTO catalog(@Valid @RequestBody CatalogRequestDTO request) {
        return service.catalog(request.sourceId());
    }
    @PostMapping("/prices/history")
    public HistoryResponseDTO history(@Valid @RequestBody PricePeriodRequestDTO request) {
        return service.history(request.sourceId(),request.regionId(),request.fuelCode(),request.from(),request.to());
    }
    @PostMapping("/prices/summary")
    public SummaryResponseDTO summary(@Valid @RequestBody PricePeriodRequestDTO request) {
        return service.summary(request.sourceId(),request.regionId(),request.fuelCode(),request.from(),request.to());
    }
    @PostMapping("/prices/compare")
    public CompareResponseDTO compare(@Valid @RequestBody CompareRequestDTO request) {
        return service.compare(request.sourceId(),request.regionA(),request.regionB(),request.fuelCode(),request.from(),request.to());
    }
    @PostMapping("/refueling/estimate")
    public EstimateResponseDTO estimate(@Valid @RequestBody EstimateRequestDTO request) { return service.estimate(request); }
}
