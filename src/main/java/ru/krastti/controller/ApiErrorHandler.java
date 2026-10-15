package ru.krastti.controller;

import ru.krastti.entity.dto.ErrorResponseDTO;

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
    public ResponseEntity<ErrorResponseDTO> domain(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponseDTO(exception.code(),exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDTO> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO("INVALID_REQUEST","Проверьте поля запроса, даты, объём, остаток и целое количество техники от 1 до 1000."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponseDTO> unavailable(DataAccessException exception) {
        return ResponseEntity.status(503).body(new ErrorResponseDTO("DATA_UNAVAILABLE","Сохранённые данные временно недоступны. Повторите запрос позже."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> internal(Exception exception) {
        return ResponseEntity.internalServerError().body(new ErrorResponseDTO("INTERNAL_ERROR","Не удалось обработать запрос."));
    }
}
