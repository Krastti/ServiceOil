package ru.krastti.exception;

public class UnknownSourceException extends ApiException {
    public UnknownSourceException() { super(404, "UNKNOWN_SOURCE", "Источник ещё не загружен."); }
}
