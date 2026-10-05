package ru.homevibe.repository;

import ru.homevibe.model.Booking;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Сервис зависит от интерфейса: файловое хранилище позже можно заменить базой данных. */
public interface BookingRepository {
    List<Booking> findAll();
    Optional<Booking> findById(UUID id);
    Optional<Booking> findByRequestKey(UUID requestKey);
    void save(Booking booking);
}
