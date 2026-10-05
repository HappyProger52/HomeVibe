package ru.homevibe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.homevibe.exception.BookingException;
import ru.homevibe.model.*;
import ru.homevibe.repository.BookingRepository;
import ru.homevibe.repository.JsonFileStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class ReviewService {
    private final ObjectMapper mapper;
    private final Path file;
    private final BookingRepository bookings;
    private final Clock clock;
    private final Map<UUID, Review> reviews = new LinkedHashMap<>();

    public ReviewService(ObjectMapper mapper, BookingRepository bookings, Clock clock,
                         @Value("${homevibe.reviews-file:data/reviews.json}") String file) {
        this.mapper=mapper; this.bookings=bookings; this.clock=clock; this.file=Path.of(file);
        List<Review> saved=JsonFileStore.read(mapper,this.file,new TypeReference<List<Review>>() {},List.of());
        Set<UUID> bookingIds=new HashSet<>();
        for (Review r:saved) {
            if (r.id()==null || r.bookingId()==null || r.username()==null || r.homeId()==null
                    || r.author()==null || r.text()==null || r.text().isBlank() || r.reply()==null
                    || r.createdAt()==null || r.rating()<1 || r.rating()>5
                    || !Set.of("pending","published","rejected").contains(r.status())
                    || reviews.putIfAbsent(r.id(),r)!=null || !bookingIds.add(r.bookingId()))
                throw new IllegalStateException("Некорректный файл отзывов");
        }
    }
    public synchronized List<Review> all() { return List.copyOf(reviews.values()); }
    public synchronized List<Review> forCustomer(String username) {
        return reviews.values().stream().filter(r->r.username().equals(username)).toList();
    }
    public synchronized Review create(UUID bookingId, String username, int rating, String text) {
        var booking=bookings.findById(bookingId).filter(b->username.equals(b.getCustomerUsername()))
                .orElseThrow(()->new BookingException(HttpStatus.NOT_FOUND,"Бронь не найдена."));
        if (booking.getStatus()!=BookingStatus.COMPLETED)
            throw new BookingException(HttpStatus.CONFLICT,"Отзыв доступен после подтверждения завершения проживания владельцем.");
        if (reviews.values().stream().anyMatch(r->r.bookingId().equals(bookingId)))
            throw new BookingException(HttpStatus.CONFLICT,"Вы уже оставили отзыв на это проживание.");
        if (rating<1 || rating>5 || text==null || text.isBlank() || text.trim().length()>2000)
            throw new BookingException(HttpStatus.BAD_REQUEST,"Укажите оценку от 1 до 5 и текст до 2000 символов.");
        var review=new Review(UUID.randomUUID(),bookingId,booking.getHomeId(),username,booking.getName(),
                rating,text.trim(),"pending","",Instant.now(clock));
        save(review); return review;
    }
    public synchronized Review moderate(UUID id,String status,String reply) {
        var review=Optional.ofNullable(reviews.get(id)).orElseThrow(()->new BookingException(HttpStatus.NOT_FOUND,"Отзыв не найден."));
        if (!Set.of("published","rejected").contains(status) || reply==null || reply.length()>2000)
            throw new BookingException(HttpStatus.BAD_REQUEST,"Выберите публикацию или отклонение. Ответ — до 2000 символов.");
        var updated=review.moderate(status,reply.trim());save(updated);return updated;
    }
    private void save(Review review) {
        var next=new LinkedHashMap<>(reviews);next.put(review.id(),review);
        JsonFileStore.write(mapper,file,next.values());reviews.put(review.id(),review);
    }
}
