package ru.krastti.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/** Вход по форме, серверная сессия и CSRF-защита административных изменений. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessions) throws Exception {
        RequestMatcher adminApi = request -> request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/admin/");
        return http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/admin/session", "/login", "/login.html", "/login.js").permitAll()
                        .requestMatchers("/admin", "/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
                        .defaultSuccessUrl("/admin", true).failureUrl("/login?error").permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/"))
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessions)
                        .expiredSessionStrategy(event -> {
                            if (adminApi.matches(event.getRequest())) {
                                error(event.getResponse(), 401, "ADMIN_UNAUTHORIZED", "Сессия завершена. Войдите снова.");
                            } else {
                                event.getResponse().sendRedirect(event.getRequest().getContextPath() + "/login?changed");
                            }
                        }))
                // Эти POST-операции только читают данные и не используют административную сессию.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/catalog", "/api/v1/prices/history",
                        "/api/v1/prices/summary", "/api/v1/prices/compare", "/api/v1/refueling/estimate",
                        "/api/v1/refueling/fleet-estimate"))
                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                                request -> !adminApi.matches(request))
                        .defaultAuthenticationEntryPointFor((request, response, failure) ->
                                error(response, 401, "ADMIN_UNAUTHORIZED", "Войдите в учётную запись администратора."), adminApi)
                        .accessDeniedHandler((request, response, failure) -> {
                            if (!adminApi.matches(request)) { response.sendError(403); return; }
                            var auth = SecurityContextHolder.getContext().getAuthentication();
                            if (auth == null || auth instanceof AnonymousAuthenticationToken) {
                                error(response, 401, "ADMIN_UNAUTHORIZED", "Войдите в учётную запись администратора.");
                            } else if (failure instanceof CsrfException) {
                                error(response, 403, "CSRF_INVALID", "Обновите страницу и повторите действие.");
                            } else {
                                error(response, 403, "ADMIN_FORBIDDEN", "Недостаточно прав для этого действия.");
                            }
                        }))
                .build();
    }

    private static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
