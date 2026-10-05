package ru.homevibe.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.homevibe.dto.BookingView;
import ru.homevibe.dto.CreateBookingRequest;
import ru.homevibe.model.BookingStatus;
import ru.homevibe.service.BookingService;

import java.util.Map;
import org.springframework.security.core.Authentication;
import java.util.UUID;

@RestController
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) { this.bookings = bookings; }

    public record StatusRequest(@NotNull BookingStatus status) {}

    @PostMapping("/api/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, ?> create(@Valid @RequestBody CreateBookingRequest request, Authentication authentication) {
        var booking = bookings.create(request, authentication.getName());
        return Map.of("id", booking.getId(), "status", booking.getStatus());
    }

    @GetMapping("/api/admin/bookings")
    public Map<String, ?> list() {
        return Map.of("bookings", bookings.findAll().stream().map(BookingView::from).toList());
    }

    @GetMapping("/api/account/bookings")
    public Map<String, ?> myBookings(Authentication authentication) {
        return Map.of("bookings", bookings.findForCustomer(authentication.getName()).stream().map(BookingView::from).toList());
    }

    @PatchMapping("/api/admin/bookings/{id}")
    public Map<String, ?> changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        bookings.changeStatus(id, request.status());
        return Map.of("ok", true);
    }
}
