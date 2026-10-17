package ru.krastti.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.krastti.entity.dto.request.RosstatImportRequestDTO;
import ru.krastti.entity.dto.response.RosstatImportResponseDTO;
import ru.krastti.service.RosstatImportCoordinator;

/** Административный API синхронного импорта недельных наблюдений Росстата. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/rosstat")
public class RosstatController {
    private final RosstatImportCoordinator imports;

    /**
     * Импортирует наблюдения внутри включительного периода длиной от 1 до 366 дней.
     * @param request обязательные даты начала и окончания; адрес источника задаётся сервером
     * @return число найденных публикаций и обработанных строк, включая повторные и исправленные
     */
    @PostMapping("/import")
    public RosstatImportResponseDTO importRange(@Valid @RequestBody RosstatImportRequestDTO request) {
        var result = imports.importRange(request.from(), request.to());
        return new RosstatImportResponseDTO(RosstatImportCoordinator.SOURCE_ID,
                result.publicationsFound(), result.rowsImported());
    }
}
