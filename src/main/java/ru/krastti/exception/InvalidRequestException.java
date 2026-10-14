package ru.krastti.exception;

public class InvalidRequestException extends ApiException {
    public InvalidRequestException(String message) { super(400, "INVALID_REQUEST", message); }
}
