package ru.homevibe.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum BookingStatus {
    PENDING, CONFIRMED, CANCELLED, COMPLETED;

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
