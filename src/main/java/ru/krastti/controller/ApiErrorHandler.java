package ru.krastti.controller;

import ru.krastti.entity.dto.Error;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.krastti.exception.ApiException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Error> domain(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(new Error(exception.code(),exception.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Error> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(new Error("INVALID_REQUEST","Проверьте поля запроса, даты, объём, остаток и целое количество техники от 1 до 1000."));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Error> unavailable(DataAccessException exception) {
        return ResponseEntity.status(503).body(new Error("DATA_UNAVAILABLE","Сохранённые данные временно недоступны. Повторите запрос позже."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Error> internal(Exception exception) {
        return ResponseEntity.internalServerError().body(new Error("INTERNAL_ERROR","Не удалось обработать запрос."));
    }
}
