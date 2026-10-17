package ru.krastti.logging;

import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import ru.krastti.service.RosstatWeeklyImportService.ImportResult;

/**
 * Измеряет внешние вызовы сервисов, записывая только заранее выбранные поля.
 * Порядок оставляет служебный ExposeInvocationInterceptor Spring перед аспектом,
 * а транзакционный перехватчик с обычным LOWEST_PRECEDENCE — внутри замера.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OperationLoggingAspect {
    private static final Logger LOG = LoggerFactory.getLogger(OperationLoggingAspect.class);
    private final long slowReadThresholdMs;

    public OperationLoggingAspect(@Value("${serviceoil.logging.slow-read-threshold-ms:500}") long slowReadThresholdMs) {
        if (slowReadThresholdMs < 0) {
            throw new IllegalArgumentException("SERVICEOIL_SLOW_READ_THRESHOLD_MS must be non-negative");
        }
        this.slowReadThresholdMs = slowReadThresholdMs;
    }

    /** Внешний advice включает завершение транзакции и повторно выбрасывает исходную ошибку. */
    @Around(value = "@annotation(operation)", argNames = "joinPoint,operation")
    public Object logOperation(ProceedingJoinPoint joinPoint, LogOperation operation) throws Throwable {
        long started = System.nanoTime();
        if (operation.level() == Level.INFO) {
            LOG.info("OPERATION_STARTED operation={}", operation.value());
        }

        final Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable failure) {
            logFailure(operation, failure, elapsedMillis(started));
            throw failure;
        }

        long elapsed = elapsedMillis(started);
        if (operation.warnIfSlow() && elapsed >= slowReadThresholdMs) {
            LOG.warn("OPERATION_SLOW operation={} durationMs={} thresholdMs={}",
                    operation.value(), elapsed, slowReadThresholdMs);
        } else if (result instanceof ImportResult imported) {
            LOG.atLevel(operation.level()).log(
                    "OPERATION_COMPLETED operation={} durationMs={} publications={} rows={}",
                    operation.value(), elapsed, imported.publicationsFound(), imported.rowsImported());
        } else {
            LOG.atLevel(operation.level()).log("OPERATION_COMPLETED operation={} durationMs={}",
                    operation.value(), elapsed);
        }
        return result;
    }

    /** Ожидаемые отказы не засоряют ERROR; сообщения и содержимое исключений не выводятся. */
    private static void logFailure(LogOperation operation, Throwable failure, long elapsed) {
        if (operation.level() == Level.INFO && failure instanceof PessimisticLockingFailureException) {
            LOG.info("OPERATION_SKIPPED operation={} code=IMPORT_BUSY exception={} durationMs={}",
                    operation.value(), failure.getClass().getSimpleName(), elapsed);
            return;
        }

        var details = LogFailureDetails.from(failure);
        Level level = details.expected() ? (operation.level() == Level.DEBUG ? Level.DEBUG : Level.WARN) : Level.ERROR;
        LOG.atLevel(level).log("OPERATION_FAILED operation={} code={} exception={} durationMs={}",
                operation.value(), details.code(), failure.getClass().getSimpleName(), elapsed);
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
