package ru.krastti.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.repository.AdminRepository;

/** Передаёт Spring Security логин и сохранённый хеш, не возвращая сущность через HTTP. */
@Service
@RequiredArgsConstructor
public class AdminDetailsService implements UserDetailsService {
    private final AdminRepository admins;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        var admin = admins.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("ADMIN_UNAUTHORIZED"));
        return User.withUsername(admin.getUsername()).password(admin.getPasswordHash())
                .roles("ADMIN").disabled(!admin.isEnabled()).build();
    }
}
