package ru.krastti.exception;

public class UnknownCombinationException extends ApiException {
    public UnknownCombinationException() {
        super(404, "UNKNOWN_COMBINATION", "Для выбранного региона и топлива нет набора данных.");
    }
}
