package ru.homevibe.dto;

import ru.homevibe.model.Booking;
import ru.homevibe.model.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** DTO не раскрывает внутренний requestKey даже в списке для владельца. */
public record BookingView(UUID id, String homeId, LocalDate start, LocalDate end, int guests,
                          String name, String phone, String note, long total,
                          BookingStatus status, Instant createdAt) {
    public static BookingView from(Booking booking) {
        return new BookingView(booking.getId(), booking.getHomeId(), booking.getStart(), booking.getEnd(),
                booking.getGuests(), booking.getName(), booking.getPhone(), booking.getNote(),
                booking.getTotal(), booking.getStatus(), booking.getCreatedAt());
    }
}
