package ru.krastti.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import ru.krastti.entity.dto.request.AdminAccountRequestDTO;
import ru.krastti.service.AdminAccountService;

/** Смена реквизитов своей учётной записи с завершением всех её действующих сессий. */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/admin/account")
@RequiredArgsConstructor
public class AdminAccountController {
    private final AdminAccountService accounts;
    private final SessionRegistry sessions;

    @PostMapping
    public ResponseEntity<Void> change(@Valid @RequestBody AdminAccountRequestDTO body, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        String username = authentication.getName();
        accounts.change(username, body);
        for (Object principal : sessions.getAllPrincipals()) {
            if (principal instanceof UserDetails user && user.getUsername().equals(username)) {
                sessions.getAllSessions(principal, false).forEach(session -> session.expireNow());
            }
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
