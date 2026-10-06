package ru.homevibe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.homevibe.model.*;
import ru.homevibe.repository.BookingRepository;
import ru.homevibe.service.ReviewService;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties="homevibe.owner-password=Test-only-password-123")
@AutoConfigureMockMvc
class ReviewsTest {
    static final Path DATA;
    static { try { DATA=Files.createTempDirectory("homevibe-reviews-"); } catch(Exception e){throw new ExceptionInInitializerError(e);} }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        for(String name:List.of("bookings","customers","houses","reviews","slides"))r.add("homevibe."+name+"-file",()->DATA.resolve(name+".json").toString());
        r.add("homevibe.uploads-directory",()->DATA.resolve("uploads").toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BookingRepository bookings;
    @Autowired Clock clock;
    @Test void administratorCanLoginAgainAfterLogout() throws Exception {
        for(int i=0;i<2;i++) {
            var result=mvc.perform(post("/api/login").with(csrf()).param("username","owner").param("password","Test-only-password-123"))
                    .andExpect(status().isOk()).andReturn();
            mvc.perform(post("/api/logout").session((org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false)).with(csrf()))
                    .andExpect(status().isOk());
        }
    }
    Booking booking(String username,BookingStatus status,LocalDate end) {
        var b=new Booking(UUID.randomUUID(),"forest",end.minusDays(2),end,2,"Тестовый гость","+79990000000","",17000,status,Instant.now(clock),UUID.randomUUID(),username);
        bookings.save(b);return b;
    }
    String payload(Booking b,int rating,String text)throws Exception {
        return mapper.writeValueAsString(Map.of("bookingId",b.getId(),"rating",rating,"text",text));
    }
    @Test void moderationPublishesOnlyApprovedReviewAndReplyAndPersists() throws Exception {
        var b=booking("reviewguest",BookingStatus.CONFIRMED,LocalDate.now(clock).minusDays(1));
        mvc.perform(patch("/api/admin/bookings/"+b.getId()).with(user("owner").roles("OWNER")).with(csrf())
                .contentType("application/json").content("{\"status\":\"completed\"}")).andExpect(status().isOk());
        var created=mvc.perform(post("/api/account/reviews").with(user("reviewguest").roles("CUSTOMER")).with(csrf())
                .contentType("application/json").content(payload(b,5,"Хороший отдых <script>alert(1)</script>")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("pending")).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/reviews")).andExpect(jsonPath("$.reviews",hasSize(0)));
        mvc.perform(get("/api/account/reviews").with(user("other").roles("CUSTOMER"))).andExpect(jsonPath("$.reviews",hasSize(0)));
        mvc.perform(post("/api/account/reviews").with(user("reviewguest").roles("CUSTOMER")).with(csrf())
                .contentType("application/json").content(payload(b,4,"Повтор"))).andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/reviews/"+id).with(user("reviewguest").roles("CUSTOMER")).with(csrf())
                .contentType("application/json").content("{\"status\":\"published\",\"reply\":\"Спасибо!\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/reviews/"+id).with(user("owner").roles("OWNER")).with(csrf())
                .contentType("application/json").content("{\"status\":\"published\",\"reply\":\"Спасибо!\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/reviews")).andExpect(jsonPath("$.reviews",hasSize(1)))
                .andExpect(jsonPath("$.reviews[0].reply").value("Спасибо!"))
                .andExpect(jsonPath("$.reviews[0].bookingId").doesNotExist()).andExpect(jsonPath("$.reviews[0].username").doesNotExist());
        var reloaded=new ReviewService(mapper,bookings,clock,DATA.resolve("reviews.json").toString());
        assertEquals("published",reloaded.forCustomer("reviewguest").getFirst().status());
        mvc.perform(patch("/api/admin/reviews/"+id).with(user("owner").roles("OWNER")).with(csrf())
                .contentType("application/json").content("{\"status\":\"rejected\",\"reply\":\"Черновик ответа\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/reviews")).andExpect(jsonPath("$.reviews",hasSize(0)));
    }
    @Test void eligibilityValidationAndCsrf() throws Exception {
        for(var status:List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.CANCELLED)) {
            var b=booking("eligible",status,LocalDate.now(clock).minusDays(1));
            mvc.perform(post("/api/account/reviews").with(user("eligible").roles("CUSTOMER")).with(csrf())
                    .contentType("application/json").content(payload(b,5,"Хорошо"))).andExpect(status().isConflict());
        }
        var b=booking("eligible",BookingStatus.COMPLETED,LocalDate.now(clock).minusDays(1));
        mvc.perform(post("/api/account/reviews").with(user("thief").roles("CUSTOMER")).with(csrf())
                .contentType("application/json").content(payload(b,5,"Чужая бронь"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/account/reviews").with(csrf()).contentType("application/json").content(payload(b,5,"Без входа"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/account/reviews").with(user("eligible").roles("CUSTOMER"))
                .contentType("application/json").content(payload(b,5,"Без CSRF"))).andExpect(status().isForbidden());
        for(String body:List.of(payload(b,0,"Текст"),payload(b,6,"Текст"),payload(b,5,"   "),payload(b,5,"x".repeat(2001))))
            mvc.perform(post("/api/account/reviews").with(user("eligible").roles("CUSTOMER")).with(csrf())
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void onlyPastConfirmedBookingCanBeCompletedAndCompletedIsFinal() throws Exception {
        for(var b:List.of(booking("a",BookingStatus.CONFIRMED,LocalDate.now(clock).plusDays(3)),
                booking("b",BookingStatus.PENDING,LocalDate.now(clock).minusDays(2)),
                booking("c",BookingStatus.CANCELLED,LocalDate.now(clock).minusDays(2)))) {
            mvc.perform(patch("/api/admin/bookings/"+b.getId()).with(user("owner").roles("OWNER")).with(csrf())
                    .contentType("application/json").content("{\"status\":\"completed\"}")).andExpect(status().isConflict());
        }
        var b=booking("d",BookingStatus.COMPLETED,LocalDate.now(clock).minusDays(2));
        mvc.perform(patch("/api/admin/bookings/"+b.getId()).with(user("owner").roles("OWNER")).with(csrf())
                .contentType("application/json").content("{\"status\":\"cancelled\"}")).andExpect(status().isConflict());
    }
}
