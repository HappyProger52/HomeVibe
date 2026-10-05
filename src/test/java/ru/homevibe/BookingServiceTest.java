package ru.homevibe;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.homevibe.dto.CreateBookingRequest;
import ru.homevibe.exception.BookingException;
import ru.homevibe.exception.StorageException;
import ru.homevibe.model.BookingStatus;
import ru.homevibe.repository.JsonBookingRepository;
import ru.homevibe.service.BookingService;
import ru.homevibe.service.HouseService;
import ru.homevibe.service.ImageService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BookingServiceTest {
    @TempDir Path directory;
    JsonBookingRepository repository;
    BookingService service;
    final Clock clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneId.of("Europe/Moscow"));

    @BeforeEach
    void prepare() {
        repository = repository();
        service = new BookingService(repository, new HouseService(JsonMapper.builder().addModule(new JavaTimeModule()).build(), new ImageService(directory.resolve("uploads").toString()), directory.resolve("houses.json").toString()), clock);
    }

    JsonBookingRepository repository() {
        return new JsonBookingRepository(JsonMapper.builder().addModule(new JavaTimeModule()).build(), directory.resolve("bookings.json").toString());
    }

    CreateBookingRequest request(String start, String end, int guests) {
        return new CreateBookingRequest("forest", LocalDate.parse(start), LocalDate.parse(end), guests,
                "Тестовый гость", "+7 000 000-00-00", "Тест", UUID.randomUUID());
    }

    @Test void calculatesPriceAndSurvivesReload() {
        var booking = service.create(request("2026-11-10", "2026-11-13", 2), "guest");
        assertEquals(25500, booking.getTotal());
        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals(booking.getId(), repository().findAll().getFirst().getId());
    }

    @Test void repeatedRequestDoesNotDuplicate() {
        var request = request("2026-11-10", "2026-11-13", 2);
        assertEquals(service.create(request, "guest").getId(), service.create(request, "guest").getId());
        assertEquals(1, service.findAll().size());
    }

    @Test void validatesDatesAndCapacity() {
        assertThrows(BookingException.class, () -> service.create(request("2026-10-04", "2026-10-06", 2), "guest"));
        assertThrows(BookingException.class, () -> service.create(request("2026-11-10", "2026-11-10", 2), "guest"));
        assertThrows(BookingException.class, () -> service.create(request("2026-11-10", "2026-11-13", 5), "guest"));
    }

    @Test void conflictAdjacentDatesAndCancellation() {
        var first = service.create(request("2026-11-10", "2026-11-13", 2), "guest");
        var second = service.create(request("2026-11-11", "2026-11-14", 2), "guest");
        service.changeStatus(first.getId(), BookingStatus.CONFIRMED);
        assertThrows(BookingException.class, () -> service.changeStatus(second.getId(), BookingStatus.CONFIRMED));
        assertDoesNotThrow(() -> service.create(request("2026-11-13", "2026-11-15", 2), "guest"));
        service.changeStatus(first.getId(), BookingStatus.CANCELLED);
        assertDoesNotThrow(() -> service.changeStatus(second.getId(), BookingStatus.CONFIRMED));
        assertEquals(BookingStatus.CANCELLED, repository().findById(first.getId()).orElseThrow().getStatus());
    }

    @Test void simultaneousConfirmationsOnlyOneSucceeds() throws Exception {
        var first = service.create(request("2026-11-10", "2026-11-13", 2), "guest");
        var second = service.create(request("2026-11-10", "2026-11-13", 2), "guest");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.Stream.of(first, second).map(booking -> executor.submit(() -> {
                start.await();
                try { service.changeStatus(booking.getId(), BookingStatus.CONFIRMED); return true; }
                catch (BookingException e) { return false; }
            })).toList();
            start.countDown();
            int success = 0;
            for (var future : futures) if (future.get(5, TimeUnit.SECONDS)) success++;
            assertEquals(1, success);
        }
    }

    @Test void corruptFileIsNotOverwritten() throws Exception {
        Path file = directory.resolve("bookings.json");
        Files.writeString(file, "broken-json");
        assertThrows(StorageException.class, this::repository);
        assertEquals("broken-json", Files.readString(file));
    }
}
