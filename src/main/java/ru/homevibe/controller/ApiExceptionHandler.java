package ru.homevibe.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.homevibe.exception.BookingException;
import ru.homevibe.exception.StorageException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<?> booking(BookingException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "Проверьте поля формы, даты и количество гостей."));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> largeImage(Exception exception) {
        return ResponseEntity.status(413).body(Map.of("error", "Фото слишком большое. Максимум 5 МБ."));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<?> storage(StorageException exception) {
        log.error("Не удалось сохранить данные", exception);
        return ResponseEntity.internalServerError().body(Map.of("error", "Не удалось сохранить заявку. Попробуйте позже."));
    }
}
