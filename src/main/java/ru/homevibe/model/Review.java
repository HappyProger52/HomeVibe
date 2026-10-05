package ru.homevibe.model;

import java.time.Instant;
import java.util.UUID;

/** Неизменяемый отзыв. Автор и дом определяются по брони на сервере. */
public record Review(UUID id, UUID bookingId, String homeId, String username, String author,
                     int rating, String text, String status, String reply, Instant createdAt) {
    public Review moderate(String status, String reply) {
        return new Review(id, bookingId, homeId, username, author, rating, text, status, reply, createdAt);
    }
}
