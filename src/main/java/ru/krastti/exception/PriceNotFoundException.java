package ru.krastti.exception;

public class PriceNotFoundException extends ApiException {
    public PriceNotFoundException() {
        super(404, "PRICE_NOT_FOUND", "На выбранную дату цена отсутствует. Выберите дату наблюдения.");
    }
}
