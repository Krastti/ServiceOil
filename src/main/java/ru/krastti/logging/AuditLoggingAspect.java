package ru.krastti.logging;

import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.krastti.entity.dto.response.VehicleTemplateResponseDTO;
import ru.krastti.logging.AuditAction.Action;

/** Фиксирует исход административного вызова снаружи авторизации и транзакционного перехватчика. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuditLoggingAspect {
    private static final Logger LOG = LoggerFactory.getLogger(AuditLoggingAspect.class);

    @Around(value = "@annotation(action)", argNames = "joinPoint,action")
    public Object audit(ProceedingJoinPoint joinPoint, AuditAction action) throws Throwable {
        // Снимок сохраняется до вызова: после смены реквизитов контроллер завершает сессии.
        String actor = actor();
        String pendingObject = action.value() == Action.ADMIN_ACCOUNT_CHANGED ? "account:" + actor : "vehicle-template:unknown";
        final Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable failure) {
            recordFailure(action.value(), actor, pendingObject, failure);
            throw failure;
        }

        try {
            String object = result instanceof VehicleTemplateResponseDTO template
                    ? "vehicle-template:" + template.id() : pendingObject;
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                // Сервис присоединился к чужой транзакции: возврат ещё не означает commit.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        record(Level.INFO, action.value(), actor, object, "SUCCESS", "NONE");
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            record(Level.WARN, action.value(), actor, object, "ROLLED_BACK", "TRANSACTION_ROLLED_BACK");
                        } else if (status == STATUS_UNKNOWN) {
                            record(Level.ERROR, action.value(), actor, object, "UNKNOWN", "TRANSACTION_COMPLETION_UNKNOWN");
                        }
                    }
                });
            } else {
                // Собственная транзакция сервиса уже завершена внутренним перехватчиком.
                record(Level.INFO, action.value(), actor, object, "SUCCESS", "NONE");
            }
        } catch (RuntimeException unavailable) {
            record(Level.WARN, action.value(), actor, pendingObject, "UNKNOWN", "AUDIT_UNAVAILABLE");
        }
        return result;
    }

    private static void recordFailure(Action action, String actor, String object, Throwable failure) {
        try {
            var details = LogFailureDetails.from(failure);
            record(details.expected() ? Level.WARN : Level.ERROR, action, actor, object,
                    details.expected() ? "REJECTED" : "FAILED", details.code());
        } catch (RuntimeException unavailable) {
            // Диагностика не должна подменять исходную ошибку бизнес-операции.
        }
    }

    private static String actor() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) return "anonymous";
            String name = authentication.getName();
            return name != null && name.matches("[\\p{L}\\p{N}_.-]{1,64}") ? name : "unavailable";
        } catch (RuntimeException unavailable) {
            return "unavailable";
        }
    }

    /** Ошибка логирования не изменяет результат уже выполненного действия. */
    private static void record(Level level, Action action, String actor, String object, String outcome, String code) {
        try {
            LOG.atLevel(level).log("AUDIT timestamp={} action={} actor={} outcome={} object={} code={}",
                    Instant.now(), action, actor, outcome, object, code);
        } catch (RuntimeException unavailable) {
            // Файловый аудит — best effort; данные и исходное исключение остаются неизменными.
        }
    }
}
