package ru.homevibe.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Инкапсуляция: состояние заявки нельзя изменить произвольными сеттерами. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Booking {
    private UUID id;
    private String homeId;
    private LocalDate start;
    private LocalDate end;
    private int guests;
    private String name;
    private String phone;
    private String note;
    private long total;
    private BookingStatus status;
    private Instant createdAt;
    private UUID requestKey;
    private String customerUsername;

    private Booking() { /* Для чтения JSON с диска. */ }

    public Booking(UUID id, String homeId, LocalDate start, LocalDate end, int guests,
                   String name, String phone, String note, long total, BookingStatus status,
                   Instant createdAt, UUID requestKey, String customerUsername) {
        this.id = id;
        this.homeId = homeId;
        this.start = start;
        this.end = end;
        this.guests = guests;
        this.name = name;
        this.phone = phone;
        this.note = note;
        this.total = total;
        this.status = status;
        this.createdAt = createdAt;
        this.requestKey = requestKey;
        this.customerUsername = customerUsername;
    }

    public boolean overlaps(LocalDate arrival, LocalDate departure) {
        return start.isBefore(departure) && end.isAfter(arrival);
    }

    public Booking withStatus(BookingStatus newStatus) {
        return new Booking(id, homeId, start, end, guests, name, phone, note,
                total, newStatus, createdAt, requestKey, customerUsername);
    }

    public String getCustomerUsername() { return customerUsername; }
    public UUID getId() { return id; }
    public String getHomeId() { return homeId; }
    public LocalDate getStart() { return start; }
    public LocalDate getEnd() { return end; }
    public int getGuests() { return guests; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getNote() { return note; }
    public long getTotal() { return total; }
    public BookingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getRequestKey() { return requestKey; }
}
