package ru.homevibe.exception;

import org.springframework.http.HttpStatus;

/** Ожидаемая ошибка бизнес-правил: её сообщение можно показать гостю. */
public class BookingException extends RuntimeException {
    private final HttpStatus status;

    public BookingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
