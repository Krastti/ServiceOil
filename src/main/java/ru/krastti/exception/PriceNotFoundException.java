package ru.krastti.exception;

public class PriceNotFoundException extends ApiException {
    public PriceNotFoundException() {
        this("На выбранную дату цена отсутствует. Выберите дату наблюдения.");
    }

    public PriceNotFoundException(String message) {
        super(404, "PRICE_NOT_FOUND", message);
    }
}
