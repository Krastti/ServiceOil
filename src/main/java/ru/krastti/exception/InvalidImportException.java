package ru.krastti.exception;

/** Безопасная ошибка CSV, источника или атомарной записи; детали БД и файла не раскрываются. */
public class InvalidImportException extends IllegalArgumentException {
    public InvalidImportException() { super("INVALID_IMPORT"); }
}
