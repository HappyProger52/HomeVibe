package ru.homevibe.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import ru.homevibe.exception.StorageException;
import ru.homevibe.model.Booking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Repository
public class JsonBookingRepository implements BookingRepository {
    private final ObjectMapper mapper;
    private final Path file;
    private final Map<UUID, Booking> bookings = new LinkedHashMap<>();

    public JsonBookingRepository(ObjectMapper mapper,
                                 @Value("${homevibe.bookings-file}") String filename) {
        this.mapper = mapper;
        this.file = Path.of(filename).toAbsolutePath();
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                List<Booking> saved = mapper.readValue(file.toFile(), new TypeReference<>() {});
                Set<UUID> keys = new HashSet<>();
                for (Booking booking : saved) {
                    if (booking == null || booking.getId() == null || booking.getRequestKey() == null
                            || booking.getHomeId() == null || booking.getStart() == null || booking.getEnd() == null
                            || !booking.getStart().isBefore(booking.getEnd()) || booking.getStatus() == null
                            || booking.getName() == null || booking.getPhone() == null || booking.getNote() == null
                            || booking.getCreatedAt() == null || booking.getGuests() < 1 || booking.getTotal() < 0
                            || bookings.containsKey(booking.getId()) || !keys.add(booking.getRequestKey())) {
                        throw new IOException("Некорректная или повторяющаяся запись в файле заявок");
                    }
                    bookings.put(booking.getId(), booking);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // Повреждённый файл нельзя молча заменить пустым — останавливаем запуск.
            throw new StorageException("Не удалось прочитать файл заявок: " + file, e);
        }
    }

    @Override
    public synchronized List<Booking> findAll() {
        return new ArrayList<>(bookings.values());
    }

    @Override
    public synchronized Optional<Booking> findById(UUID id) {
        return Optional.ofNullable(bookings.get(id));
    }

    @Override
    public synchronized Optional<Booking> findByRequestKey(UUID requestKey) {
        return bookings.values().stream().filter(b -> b.getRequestKey().equals(requestKey)).findFirst();
    }

    @Override
    public synchronized void save(Booking booking) {
        Map<UUID, Booking> updated = new LinkedHashMap<>(bookings);
        updated.put(booking.getId(), booking);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(file.getParent(), "bookings-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), updated.values());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            // Память обновляем только после успешной записи файла.
            bookings.clear();
            bookings.putAll(updated);
        } catch (IOException e) {
            throw new StorageException("Не удалось сохранить заявку", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Не скрываем исходную ошибку. */ }
            }
        }
    }
}
