package ru.krastti.exception;

/** Основной импорт откатился, но записать FAILED в независимой транзакции не удалось. */
public class ImportJournalUnavailableException extends IllegalArgumentException {
    public ImportJournalUnavailableException() { super("IMPORT_JOURNAL_UNAVAILABLE"); }
}
