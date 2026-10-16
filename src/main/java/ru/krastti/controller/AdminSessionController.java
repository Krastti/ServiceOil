package ru.krastti.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import ru.krastti.entity.dto.AdminSessionResponseDTO;

/** Сведения о сессии для формы входа и административной панели. */
@RestController
public class AdminSessionController {
    /** Возвращает свежий CSRF-токен до входа и после смены сессии; ответ не кешируется. */
    @GetMapping("/api/v1/admin/session")
    public ResponseEntity<AdminSessionResponseDTO> session(Authentication authentication, CsrfToken csrf) {
        boolean admin = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AdminSessionResponseDTO(
                admin, admin ? authentication.getName() : null, csrf.getToken(), csrf.getHeaderName(), csrf.getParameterName()));
    }
}
