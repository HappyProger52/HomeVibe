package ru.homevibe.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.homevibe.service.BookingService;
import ru.homevibe.service.HouseService;
import java.time.LocalDate;
import java.util.Map;

@RestController
public class CatalogController {
    private final HouseService houses;
    private final BookingService bookings;

    public CatalogController(HouseService houses, BookingService bookings) {
        this.houses = houses;
        this.bookings = bookings;
    }

    public record OccupiedDates(String homeId, LocalDate start, LocalDate end) {}

    @GetMapping("/api/homes")
    public Map<String, ?> catalog() {
        // Публичный ответ содержит только даты, без контактов гостей.
        return Map.of("homes", houses.findAll(), "unavailable", bookings.confirmedBookings().stream()
                .map(b -> new OccupiedDates(b.getHomeId(), b.getStart(), b.getEnd())).toList());
    }
}
