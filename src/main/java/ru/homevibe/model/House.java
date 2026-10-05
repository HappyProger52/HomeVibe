package ru.homevibe.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Record — неизменяемый объект с автоматически созданными геттерами и конструктором. */
public record House(String id, String name, String category, int guests, int bedrooms,
                    int size, long price, String tag, String location, String style,
                    String description, String imageUrl, LocalDate availableFrom, LocalDate availableUntil,
                    LocalTime checkIn, LocalTime checkOut, List<DateRange> blockedDates) {
    public House {
        blockedDates = List.copyOf(blockedDates);
    }

    public boolean available(LocalDate start, LocalDate end) {
        return (availableFrom == null || !start.isBefore(availableFrom))
                && (availableUntil == null || !end.isAfter(availableUntil))
                && blockedDates.stream().noneMatch(range -> range.overlaps(start, end));
    }
}
