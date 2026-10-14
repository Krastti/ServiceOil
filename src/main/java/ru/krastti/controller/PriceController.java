package ru.krastti.controller;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.krastti.entity.dto.*;
import ru.krastti.service.PriceService;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PriceController {
    private final PriceService service;


    @GetMapping("/catalog")
    public Catalog catalog(@RequestParam(defaultValue="ROSSTAT_PFO") String sourceId) { return service.catalog(sourceId); }
    @GetMapping("/prices/history")
    public History history(@RequestParam(defaultValue="ROSSTAT_PFO") String sourceId,@RequestParam String regionId,
                           @RequestParam String fuelCode,@RequestParam LocalDate from,@RequestParam LocalDate to) {
        return service.history(sourceId,regionId,fuelCode,from,to);
    }
    @GetMapping("/prices/summary")
    public Summary summary(@RequestParam(defaultValue="ROSSTAT_PFO") String sourceId,@RequestParam String regionId,
                           @RequestParam String fuelCode,@RequestParam LocalDate from,@RequestParam LocalDate to) {
        return service.summary(sourceId,regionId,fuelCode,from,to);
    }
    @GetMapping("/prices/compare")
    public Compare compare(@RequestParam(defaultValue="ROSSTAT_PFO") String sourceId,@RequestParam String regionA,@RequestParam String regionB,
                           @RequestParam String fuelCode,@RequestParam LocalDate from,@RequestParam LocalDate to) {
        return service.compare(sourceId,regionA,regionB,fuelCode,from,to);
    }
    @PostMapping("/refueling/estimate")
    public Estimate estimate(@Valid @RequestBody EstimateRequest request) { return service.estimate(request); }
}
