package ru.krastti.logging;

import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.krastti.exception.ApiException;
import ru.krastti.exception.ImportJournalUnavailableException;
import ru.krastti.exception.InvalidImportException;
import ru.krastti.exception.RosstatSourceException;

/** Общая классификация отказов для технических логов и аудита без сообщений исключений. */
record LogFailureDetails(String code, boolean expected) {
    static LogFailureDetails from(Throwable failure) {
        return switch (failure) {
            case AuthenticationException ignored -> new LogFailureDetails("ADMIN_UNAUTHORIZED", true);
            case AccessDeniedException ignored -> new LogFailureDetails(accessDeniedCode(), true);
            case RosstatSourceException source -> new LogFailureDetails(safeCode(source.code()), true);
            case InvalidImportException ignored -> new LogFailureDetails("INVALID_IMPORT", true);
            case ImportJournalUnavailableException ignored -> new LogFailureDetails("IMPORT_JOURNAL_UNAVAILABLE", true);
            case ApiException api -> new LogFailureDetails(safeCode(api.code()), api.status() >= 400 && api.status() < 500);
            case DataAccessException ignored -> new LogFailureDetails("DATA_UNAVAILABLE", false);
            default -> new LogFailureDetails("INTERNAL_ERROR", false);
        };
    }

    private static String accessDeniedCode() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken ? "ADMIN_UNAUTHORIZED" : "ADMIN_FORBIDDEN";
    }

    private static String safeCode(String code) {
        return code != null && code.matches("[A-Z][A-Z0-9_]{0,63}") ? code : "INTERNAL_ERROR";
    }
}
