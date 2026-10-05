package ru.homevibe.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.homevibe.dto.CreateBookingRequest;
import ru.homevibe.dto.UpdateHouseRequest;
import ru.homevibe.exception.BookingException;
import ru.homevibe.model.*;
import ru.homevibe.repository.BookingRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {
    private final BookingRepository repository;
    private final HouseService houses;
    private final Clock clock;

    // Constructor DI: Spring сам передаст подходящие бины.
    public BookingService(BookingRepository repository, HouseService houses, Clock clock) {
        this.repository = repository;
        this.houses = houses;
        this.clock = clock;
    }

    public List<Booking> findAll() { return repository.findAll(); }

    public List<Booking> confirmedBookings() {
        return repository.findAll().stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .filter(b -> !b.getEnd().isBefore(LocalDate.now(clock))).toList();
    }

    // Проверка и сохранение выполняются под одной блокировкой в рамках одного приложения.
    public synchronized Booking create(CreateBookingRequest request, String customerUsername) {
        var existing = repository.findByRequestKey(request.requestKey());
        if (existing.isPresent()) {
            Booking b = existing.get();
            if (!java.util.Objects.equals(b.getCustomerUsername(), customerUsername) || !b.getHomeId().equals(request.homeId()) || !b.getStart().equals(request.start())
                    || !b.getEnd().equals(request.end()) || b.getGuests() != request.guests()
                    || !b.getName().equals(request.name().trim()) || !b.getPhone().equals(request.phone().trim())
                    || !b.getNote().equals(request.note().trim())) {
                throw new BookingException(HttpStatus.CONFLICT, "Эта заявка уже отправлена с другими данными. Откройте форму заново.");
            }
            return b;
        }
        House house = houses.findById(request.homeId());
        long nights = ChronoUnit.DAYS.between(request.start(), request.end());
        if (request.start().isBefore(LocalDate.now(clock)) || nights < 1 || nights > 365) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "Укажите будущие даты и срок от 1 до 365 ночей.");
        }
        String digits = request.phone().replaceAll("\\D", "");
        if (request.guests() < 1 || request.guests() > house.guests()
                || request.name().trim().length() < 2 || digits.length() < 10 || digits.length() > 15) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "Проверьте имя, телефон и количество гостей.");
        }
        ensureAvailable(house.id(), request.start(), request.end(), null);
        Booking booking = new Booking(UUID.randomUUID(), house.id(), request.start(), request.end(),
                request.guests(), request.name().trim(), request.phone().trim(), request.note().trim(),
                nights * house.price(), BookingStatus.PENDING, Instant.now(clock), request.requestKey(), customerUsername);
        repository.save(booking);
        return booking;
    }

    public synchronized void changeStatus(UUID id, BookingStatus status) {
        Booking booking = repository.findById(id)
                .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "Заявка не найдена."));
        if (status == BookingStatus.PENDING || booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED
                || (status == BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING)) {
            throw new BookingException(HttpStatus.CONFLICT, "Такое изменение статуса недоступно. Обновите список.");
        }
        if (status == BookingStatus.COMPLETED && (booking.getStatus() != BookingStatus.CONFIRMED
                || !booking.getEnd().isBefore(LocalDate.now(clock)))) {
            throw new BookingException(HttpStatus.CONFLICT, "Завершить можно подтверждённое проживание после даты выезда.");
        }
        if (status == BookingStatus.CONFIRMED) {
            if (booking.getGuests() > houses.findById(booking.getHomeId()).guests()) {
                throw new BookingException(HttpStatus.CONFLICT, "Количество гостей превышает текущую вместимость дома.");
            }
            if (booking.getStart().isBefore(LocalDate.now(clock))) {
                throw new BookingException(HttpStatus.CONFLICT, "Дата заезда уже прошла.");
            }
            ensureAvailable(booking.getHomeId(), booking.getStart(), booking.getEnd(), id);
        }
        repository.save(booking.withStatus(status));
    }

    public List<Booking> findForCustomer(String username) {
        return repository.findAll().stream().filter(b -> username.equals(b.getCustomerUsername())).toList();
    }

    public synchronized House updateHouse(String id, UpdateHouseRequest request) {
        House updated = houses.prepareUpdate(id, request);
        boolean conflict = confirmedBookings().stream().filter(b -> b.getHomeId().equals(id))
                .anyMatch(b -> !updated.available(b.getStart(), b.getEnd()) || b.getGuests() > updated.guests());
        if (conflict) throw new BookingException(HttpStatus.CONFLICT, "Изменения затрагивают подтверждённую бронь. Сначала согласуйте её отмену.");
        houses.save(updated);
        return updated;
    }

    private void ensureAvailable(String homeId, LocalDate start, LocalDate end, UUID exclude) {
        if (!houses.findById(homeId).available(start, end)) throw new BookingException(HttpStatus.CONFLICT, "Дом недоступен на выбранные даты.");
        boolean occupied = repository.findAll().stream().anyMatch(b -> b.getStatus() == BookingStatus.CONFIRMED
                && b.getHomeId().equals(homeId) && !b.getId().equals(exclude) && b.overlaps(start, end));
        if (occupied) throw new BookingException(HttpStatus.CONFLICT, "Дом уже занят на эти даты.");
    }
}
