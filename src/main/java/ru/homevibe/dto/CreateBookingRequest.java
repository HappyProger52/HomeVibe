package ru.homevibe.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
        @NotBlank String homeId,
        @NotNull LocalDate start,
        @NotNull LocalDate end,
        @Min(1) @Max(8) int guests,
        @NotBlank @Size(min = 2, max = 80) String name,
        @NotBlank @Size(max = 30) @Pattern(regexp = "\\+?[0-9 ()-]+") String phone,
        @NotNull @Size(max = 600) String note,
        @NotNull UUID requestKey) {
}
