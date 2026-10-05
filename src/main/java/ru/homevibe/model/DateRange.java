package ru.homevibe.model;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Правая граница исключается: в день окончания закрытия снова можно заехать. */
public record DateRange(@NotNull LocalDate start, @NotNull LocalDate end) {
    public boolean overlaps(LocalDate arrival, LocalDate departure) {
        return start.isBefore(departure) && end.isAfter(arrival);
    }
}
