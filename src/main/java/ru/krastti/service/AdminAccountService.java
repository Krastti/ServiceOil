package ru.krastti.service;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.entity.dto.request.AdminAccountRequestDTO;
import ru.krastti.exception.ApiException;
import ru.krastti.exception.InvalidRequestException;
import ru.krastti.repository.AdminRepository;

/** Атомарно меняет реквизиты после проверки текущего пароля; открытые пароли не сохраняются. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class AdminAccountService {
    private final AdminRepository admins;
    private final PasswordEncoder passwords;

    @Transactional
    public void change(String currentUsername, AdminAccountRequestDTO request) {
        var admin = admins.findForUpdate(currentUsername)
                .filter(account -> account.isEnabled())
                .orElseThrow(() -> new ApiException(401, "ADMIN_UNAUTHORIZED", "Войдите в учётную запись администратора."));
        if (!passwords.matches(request.currentPassword(), admin.getPasswordHash())) {
            throw new ApiException(400, "CURRENT_PASSWORD_INVALID", "Текущий пароль указан неверно.");
        }
        var username = request.username().strip();
        if (username.length() < 3 || !username.matches("[\\p{L}\\p{N}_.-]+")) {
            throw new InvalidRequestException("Логин: от 3 до 64 символов, буквы, цифры, точка, дефис или подчёркивание.");
        }
        String newPassword = request.newPassword();
        if (newPassword != null && (newPassword.isBlank()
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72)) {
            throw new InvalidRequestException("Новый пароль: не менее 8 символов, не более 72 байт в UTF-8.");
        }
        boolean changedPassword = newPassword != null && !passwords.matches(newPassword, admin.getPasswordHash());
        if (username.equals(admin.getUsername()) && !changedPassword) {
            throw new InvalidRequestException("Укажите новый логин или новый пароль.");
        }
        if (admins.existsByUsernameAndIdNot(username, admin.getId())) throw usernameTaken();
        admin.changeCredentials(username, changedPassword ? passwords.encode(newPassword) : null);
        try { admins.saveAndFlush(admin); }
        catch (DataIntegrityViolationException conflict) { throw usernameTaken(); }
    }

    private static ApiException usernameTaken() {
        return new ApiException(409, "ADMIN_USERNAME_TAKEN", "Этот логин уже используется. Выберите другой.");
    }
}
