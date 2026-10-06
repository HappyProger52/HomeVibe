package ru.homevibe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "homevibe.owner-password=Test-only-password-123")
@AutoConfigureMockMvc
class ApiTest {
    static final Path DATA;
    static {
        try { DATA = Files.createTempDirectory("homevibe-java-test-").resolve("bookings.json"); }
        catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("homevibe.bookings-file", () -> DATA.toString());
        registry.add("homevibe.slides-file", () -> DATA.resolveSibling("slides.json").toString());
        registry.add("homevibe.reviews-file", () -> DATA.resolveSibling("reviews.json").toString());
        registry.add("homevibe.customers-file", () -> DATA.resolveSibling("customers.json").toString());
        registry.add("homevibe.houses-file", () -> DATA.resolveSibling("houses.json").toString());
        registry.add("homevibe.uploads-directory", () -> DATA.resolveSibling("uploads").toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test void staticWebsiteAndCatalogArePublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/api/homes")).andExpect(status().isOk()).andExpect(jsonPath("$.homes", hasSize(3)));
        mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test void privateDataAndCsrfAreProtected() throws Exception {
        mvc.perform(get("/api/admin/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/login").with(csrf()).param("username", "owner").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test void malformedInputProducesReadableError() throws Exception {
        mvc.perform(post("/api/bookings").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
        mvc.perform(post("/api/bookings").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test void completeBookingLoginConfirmLogoutFlow() throws Exception {
        String payload = mapper.writeValueAsString(Map.of("homeId", "lake", "start", "2099-11-20", "end", "2099-11-23",
                "guests", 2, "name", "Тестовый клиент", "phone", "+7 000 000-00-00", "note", "Тест",
                "requestKey", UUID.randomUUID()));
        var created = mvc.perform(post("/api/bookings").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("pending")).andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        var login = mvc.perform(post("/api/login").with(csrf()).param("username", "owner").param("password", "Test-only-password-123"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/session").session(session)).andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(get("/api/admin/bookings").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[0].total").value(19500))
                .andExpect(jsonPath("$.bookings[0].requestKey").doesNotExist());
        mvc.perform(patch("/api/admin/bookings/" + id).session(session).with(csrf())
                        .contentType("application/json").content("{\"status\":\"confirmed\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/homes")).andExpect(jsonPath("$.unavailable[0].homeId").value("lake"))
                .andExpect(jsonPath("$.unavailable[0].phone").doesNotExist());
        mvc.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/bookings")).andExpect(status().isUnauthorized());
    }
}
