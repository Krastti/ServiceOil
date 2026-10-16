package ru.krastti.exception;

/** Безопасная ошибка провайдера: не содержит URL, HTML, заголовков или исходного сообщения сети. */
public class RosstatSourceException extends IllegalArgumentException {
    private final String code;
    private final int status;

    public RosstatSourceException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }

    public static RosstatSourceException unavailable() { return new RosstatSourceException("ROSSTAT_UNAVAILABLE", 503); }
    public static RosstatSourceException format() { return new RosstatSourceException("ROSSTAT_FORMAT_ERROR", 502); }
}
