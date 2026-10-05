package ru.homevibe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.homevibe.model.DateRange;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record UpdateHouseRequest(
        @NotBlank @Size(max=80) String name,
        @NotBlank @Pattern(regexp="couple|family|friends") String category,
        @Min(1) @Max(30) int guests, @Min(1) @Max(20) int bedrooms,
        @Min(1) @Max(1000) int size, @Min(1) @Max(1000000) long price,
        @NotBlank @Size(max=50) String tag, @NotBlank @Size(max=150) String location,
        @NotBlank @Size(max=2000) String description, @NotBlank @Size(max=100) String imageUrl,
        LocalDate availableFrom, LocalDate availableUntil,
        @NotNull LocalTime checkIn, @NotNull LocalTime checkOut,
        @NotNull @Size(max=100) List<@NotNull @Valid DateRange> blockedDates) {}
