package ru.homevibe.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.homevibe.dto.UpdateHouseRequest;
import ru.homevibe.model.House;
import ru.homevibe.service.*;
import java.util.Map;

@RestController
public class HouseAdminController {
    private final BookingService bookings;
    private final ImageService images;
    private final HouseService houses;
    public HouseAdminController(BookingService bookings, ImageService images, HouseService houses) {
        this.bookings = bookings;
        this.images = images;
        this.houses = houses;
    }

    @PostMapping("/api/admin/houses")
    @ResponseStatus(HttpStatus.CREATED)
    public House create(@Valid @RequestBody UpdateHouseRequest request) {
        return houses.create(request);
    }

    @PutMapping("/api/admin/houses/{id}")
    public House update(@PathVariable String id, @Valid @RequestBody UpdateHouseRequest request) {
        return bookings.updateHouse(id, request);
    }

    @PostMapping("/api/admin/images")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        return Map.of("url", images.upload(file));
    }

    @GetMapping("/uploads/{filename}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String filename) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(images.path(filename)));
    }
}
