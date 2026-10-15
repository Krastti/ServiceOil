package ru.krastti.exception;

public class UnknownCombinationException extends ApiException {
    public UnknownCombinationException() {
        this("Для выбранного региона и топлива нет набора данных.");
    }

    public UnknownCombinationException(String message) {
        super(404, "UNKNOWN_COMBINATION", message);
    }
}
