package ru.krastti.controller;

import ru.krastti.entity.dto.response.ErrorResponseDTO;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.krastti.entity.dto.request.AdminAccountRequestDTO;
import ru.krastti.exception.ApiException;
import ru.krastti.exception.RosstatSourceException;
import ru.krastti.exception.InvalidImportException;
import ru.krastti.exception.ImportJournalUnavailableException;
import org.springframework.dao.PessimisticLockingFailureException;

@RestControllerAdvice(annotations = RestController.class)
public class ApiErrorHandler {

    /** Отказ метода сервиса сохраняет тот же HTTP-контракт, что и фильтры безопасности. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> unauthenticated(AuthenticationException exception) {
        return adminUnauthorized();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> forbidden(AccessDeniedException exception) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return adminUnauthorized();
        return ResponseEntity.status(403).body(new ErrorResponseDTO("ADMIN_FORBIDDEN", "Недостаточно прав для этого действия."));
    }

    private static ResponseEntity<ErrorResponseDTO> adminUnauthorized() {
        return ResponseEntity.status(401).body(new ErrorResponseDTO("ADMIN_UNAUTHORIZED", "Войдите в учётную запись администратора."));
    }

    @ExceptionHandler(RosstatSourceException.class)
    public ResponseEntity<ErrorResponseDTO> source(RosstatSourceException exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponseDTO(exception.code(),
                exception.status() == 502 ? "Формат публикации Росстата не распознан." : "Росстат временно недоступен."));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDTO> busy(PessimisticLockingFailureException exception) {
        return ResponseEntity.status(409).body(new ErrorResponseDTO("IMPORT_BUSY", "Импорт уже выполняется. Повторите запрос позже."));
    }

    @ExceptionHandler(InvalidImportException.class)
    public ResponseEntity<ErrorResponseDTO> importRejected(InvalidImportException exception) {
        return ResponseEntity.status(422).body(new ErrorResponseDTO("INVALID_IMPORT", "Набор данных отклонён, изменения отменены."));
    }

    @ExceptionHandler(ImportJournalUnavailableException.class)
    public ResponseEntity<ErrorResponseDTO> journal(ImportJournalUnavailableException exception) {
        return ResponseEntity.status(503).body(new ErrorResponseDTO("IMPORT_JOURNAL_UNAVAILABLE", "Журнал импорта временно недоступен."));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponseDTO> domain(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponseDTO(exception.code(),exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDTO> invalid(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException invalid
                && invalid.getBindingResult().getTarget() instanceof AdminAccountRequestDTO) {
            return ResponseEntity.badRequest().body(new ErrorResponseDTO("INVALID_REQUEST",
                    "Проверьте логин (3–64 символа), текущий пароль и новый пароль (от 8 символов, если указан)."));
        }
        return ResponseEntity.badRequest().body(new ErrorResponseDTO("INVALID_REQUEST","Проверьте поля запроса, даты, объём, остаток и целое количество техники от 1 до 1000."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponseDTO> unavailable(DataAccessException exception) {
        return ResponseEntity.status(503).body(new ErrorResponseDTO("DATA_UNAVAILABLE","Сохранённые данные временно недоступны. Повторите запрос позже."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> internal(Exception exception) {
        return ResponseEntity.internalServerError().body(new ErrorResponseDTO("INTERNAL_ERROR","Не удалось обработать запрос."));
    }
}
