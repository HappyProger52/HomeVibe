package ru.homevibe.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.homevibe.model.Review;
import ru.homevibe.service.ReviewService;
import java.time.Instant;
import java.util.*;

@RestController
public class ReviewController {
    private final ReviewService reviews;
    public ReviewController(ReviewService reviews) { this.reviews=reviews; }
    public record Create(@NotNull UUID bookingId, @Min(1) @Max(5) int rating, @NotBlank @Size(max=2000) String text) {}
    public record Moderate(@NotNull @Pattern(regexp="published|rejected") String status, @NotNull @Size(max=2000) String reply) {}
    // Публичный ответ не содержит логин, контакты или идентификатор брони.
    public record PublicReview(UUID id,String homeId,String author,int rating,String text,String reply,Instant createdAt) {
        static PublicReview from(Review r) { return new PublicReview(r.id(),r.homeId(),r.author(),r.rating(),r.text(),r.reply(),r.createdAt()); }
    }
    @GetMapping("/api/reviews") public Map<String,?> published() {
        return Map.of("reviews",reviews.all().stream().filter(r->r.status().equals("published")).map(PublicReview::from).toList());
    }
    @GetMapping("/api/account/reviews") public Map<String,?> mine(Authentication auth) {
        return Map.of("reviews",reviews.forCustomer(auth.getName()).stream()
                .map(r->r.status().equals("published")?r:r.moderate(r.status(),"")).toList());
    }
    @PostMapping("/api/account/reviews") @ResponseStatus(HttpStatus.CREATED)
    public Review create(@Valid @RequestBody Create request,Authentication auth) {
        return reviews.create(request.bookingId(),auth.getName(),request.rating(),request.text());
    }
    @GetMapping("/api/admin/reviews") public Map<String,?> all() { return Map.of("reviews",reviews.all()); }
    @PatchMapping("/api/admin/reviews/{id}") public Review moderate(@PathVariable UUID id,@Valid @RequestBody Moderate request) {
        return reviews.moderate(id,request.status(),request.reply());
    }
}
