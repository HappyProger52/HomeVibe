package ru.homevibe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.homevibe.dto.UpdateHouseRequest;
import ru.homevibe.exception.BookingException;
import ru.homevibe.model.House;
import ru.homevibe.repository.JsonFileStore;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.*;

@Service
public class HouseService {
    private final Map<String, House> houses = new LinkedHashMap<>();
    private final ObjectMapper mapper;
    private final Path file;
    private final ImageService images;

    public HouseService(ObjectMapper mapper, ImageService images,
                        @Value("${homevibe.houses-file:data/houses.json}") String filename) {
        this.mapper = mapper;
        this.images = images;
        this.file = Path.of(filename).toAbsolutePath();
        List<House> defaults = List.of(
            new House("forest", "Лесной дом", "family", 4, 2, 68, 8500, "Среди сосен", "Сосновый лес · Своя терраса", "",
                "Уютный дом среди сосен с собственной террасой. Две спальни, кухня и место для неспешных вечеров.", "/cabin.svg", null, null, LocalTime.of(15,0), LocalTime.NOON, List.of()),
            new House("lake", "Тихий берег", "couple", 2, 1, 42, 6500, "Для двоих", "У воды · Панорамные окна", "lake",
                "Небольшой дом для двоих. Завтраки с видом на природу и тихие прогулки у воды.", "/cabin.svg", null, null, LocalTime.of(15,0), LocalTime.NOON, List.of()),
            new House("friends", "Большая поляна", "friends", 8, 4, 120, 14000, "Вместе теплее", "Лесная поляна · Просторная гостиная", "friends",
                "Просторный дом для семьи и друзей: четыре спальни, большая гостиная и общая терраса.", "/cabin.svg", null, null, LocalTime.of(15,0), LocalTime.NOON, List.of())
        );
        List<House> stored = JsonFileStore.read(mapper, file, new TypeReference<>() {}, defaults);
        for (House house : stored) {
            if (house == null || house.id() == null || houses.putIfAbsent(house.id(), house) != null) {
                throw new IllegalStateException("Повреждён файл домов");
            }
        }
    }

    public synchronized List<House> findAll() { return List.copyOf(houses.values()); }
    public synchronized House findById(String id) {
        House house = houses.get(id);
        if (house == null) throw new BookingException(HttpStatus.NOT_FOUND, "Дом не найден.");
        return house;
    }

    public House prepareUpdate(String id, UpdateHouseRequest request) {
        House old = findById(id);
        return prepare(id, old.style(), request);
    }

    public synchronized House create(UpdateHouseRequest request) {
        House house = prepare(UUID.randomUUID().toString(), "", request);
        save(house);
        return house;
    }

    private House prepare(String id, String style, UpdateHouseRequest request) {
        images.validateUrl(request.imageUrl());
        if (request.availableFrom() != null && request.availableUntil() != null
                && !request.availableFrom().isBefore(request.availableUntil())) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "Начало доступности должно быть раньше последнего выезда.");
        }
        if (request.blockedDates().stream().anyMatch(range -> !range.start().isBefore(range.end()))) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "У закрытых дат окончание должно быть позже начала.");
        }
        return new House(id, request.name().trim(), request.category(), request.guests(), request.bedrooms(), request.size(),
                request.price(), request.tag().trim(), request.location().trim(), style, request.description().trim(),
                request.imageUrl(), request.availableFrom(), request.availableUntil(), request.checkIn(), request.checkOut(), request.blockedDates());
    }

    public synchronized void save(House house) {
        Map<String, House> updated = new LinkedHashMap<>(houses);
        updated.put(house.id(), house);
        JsonFileStore.write(mapper, file, updated.values());
        houses.clear();
        houses.putAll(updated);
    }
}
