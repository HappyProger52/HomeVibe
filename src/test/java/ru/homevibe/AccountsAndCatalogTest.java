package ru.homevibe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.homevibe.service.CustomerService;
import ru.homevibe.service.HouseService;
import ru.homevibe.service.ImageService;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties={"homevibe.owner-password=Test-only-password-123", "homevibe.owner-login=forestadmin"})
@AutoConfigureMockMvc
class AccountsAndCatalogTest {
    static final Path DATA;
    static { try { DATA=Files.createTempDirectory("homevibe-accounts-"); } catch(Exception e){throw new ExceptionInInitializerError(e);} }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("homevibe.bookings-file",()->DATA.resolve("bookings.json").toString());
        registry.add("homevibe.slides-file",()->DATA.resolve("slides.json").toString());
        registry.add("homevibe.reviews-file",()->DATA.resolve("reviews.json").toString());
        registry.add("homevibe.customers-file",()->DATA.resolve("customers.json").toString());
        registry.add("homevibe.houses-file",()->DATA.resolve("houses.json").toString());
        registry.add("homevibe.uploads-directory",()->DATA.resolve("uploads").toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;

    MockHttpSession registerAndLogin(String username) throws Exception {
        mvc.perform(post("/api/register").with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("username",username,"name","Тестовый гость","password","Customer-password-123"))))
                .andExpect(status().isOk());
        var result=mvc.perform(post("/api/login").with(csrf()).param("username",username).param("password","Customer-password-123"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }

    Map<String,Object> request(String home,String start,String end) {
        Map<String,Object> data=new HashMap<>(Map.of("homeId",home,"start",start,"end",end,"guests",2,
                "name","Гость","phone","+7 000 000-00-00","note","Тест","requestKey",UUID.randomUUID()));
        data.put("customerUsername","victim"); // Клиент не может назначить владельца заявки.
        return data;
    }

    @Test void customerSeesOnlyOwnBookingsAndPersistsAfterReload() throws Exception {
        var alice=registerAndLogin("alice");
        var bob=registerAndLogin("bob");
        var created=mvc.perform(post("/api/bookings").session(alice).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(request("forest","2099-05-10","2099-05-13")))).andExpect(status().isCreated()).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/account/bookings").session(alice)).andExpect(jsonPath("$.bookings",hasSize(1)))
                .andExpect(jsonPath("$.bookings[0].status").value("pending"));
        mvc.perform(get("/api/account/bookings").session(bob)).andExpect(jsonPath("$.bookings",hasSize(0)));
        mvc.perform(get("/api/admin/bookings").session(alice)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/bookings/"+id).session(bob).with(csrf()).contentType("application/json").content("{\"status\":\"confirmed\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/bookings/"+id).with(user("forestadmin").roles("OWNER")).with(csrf())
                .contentType("application/json").content("{\"status\":\"confirmed\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/account/bookings").session(alice)).andExpect(jsonPath("$.bookings[0].status").value("confirmed"));
        var reloaded=new CustomerService(mapper,encoder,DATA.resolve("customers.json").toString(),"forestadmin");
        var customer=reloaded.find("alice").orElseThrow();assertTrue(encoder.matches("Customer-password-123",customer.passwordHash()));
        assertFalse(Files.readString(DATA.resolve("customers.json")).contains("Customer-password-123"));
    }

    @Test void separateAdminLoginAndReservedUsername() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(forwardedUrl("/admin.html"));
        mvc.perform(get("/account")).andExpect(status().isOk()).andExpect(forwardedUrl("/account.html"));
        mvc.perform(post("/api/register").with(csrf()).contentType("application/json")
                .content("{\"username\":\"forestadmin\",\"name\":\"Гость\",\"password\":\"Customer-password-123\"}"))
                .andExpect(status().isConflict());
        var result=mvc.perform(post("/api/login").with(csrf()).param("username","forestadmin").param("password","Test-only-password-123"))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/session").session((MockHttpSession)result.getRequest().getSession(false)))
                .andExpect(jsonPath("$.role").value("OWNER")).andExpect(jsonPath("$.username").value("forestadmin"));
    }

    ObjectNode house(String id) throws Exception {
        var response=mvc.perform(get("/api/homes")).andReturn();
        for(var house:mapper.readTree(response.getResponse().getContentAsString()).get("homes"))if(house.get("id").asText().equals(id))return (ObjectNode)house;
        throw new AssertionError("House missing");
    }

    @Test void adminEditsHouseUploadsPhotoAndClosesDates() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var uploaded=mvc.perform(multipart("/api/admin/images").file(new MockMultipartFile("file","photo.png","image/png",bytes.toByteArray()))
                .with(user("admin").roles("OWNER")).with(csrf())).andExpect(status().isOk()).andReturn();
        String image=mapper.readTree(uploaded.getResponse().getContentAsString()).get("url").asText();
        mvc.perform(get(image)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(multipart("/api/admin/images").file(new MockMultipartFile("file","fake.png","image/png","<script>bad</script>".getBytes()))
                .with(user("admin").roles("OWNER")).with(csrf())).andExpect(status().isBadRequest());
        ObjectNode update=house("lake");update.put("name","Новый берег");update.put("description","Новое описание");update.put("imageUrl",image);
        update.put("availableFrom","2099-06-01");update.put("availableUntil","2099-09-01");update.put("checkIn","16:00:00");
        update.putArray("blockedDates").addObject().put("start","2099-07-01").put("end","2099-07-10");
        mvc.perform(put("/api/admin/houses/lake").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json").content(update.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/houses/lake").with(user("admin").roles("OWNER")).with(csrf()).contentType("application/json").content(update.toString()))
                .andExpect(status().isOk());
        var reloaded=new HouseService(mapper,new ImageService(DATA.resolve("uploads").toString()),DATA.resolve("houses.json").toString());
        assertEquals("Новый берег",reloaded.findById("lake").name());assertEquals(image,reloaded.findById("lake").imageUrl());
        for(var dates:List.of(new String[]{"2099-05-20","2099-05-22"},new String[]{"2099-07-05","2099-07-12"})) {
            mvc.perform(post("/api/bookings").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json")
                    .content(mapper.writeValueAsString(request("lake",dates[0],dates[1])))).andExpect(status().isConflict());
        }
        mvc.perform(post("/api/bookings").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(request("lake","2099-07-10","2099-07-12")))).andExpect(status().isCreated());
    }

    @Test void closingDatesCannotInvalidateConfirmedBooking() throws Exception {
        var created=mvc.perform(post("/api/bookings").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(request("friends","2099-10-10","2099-10-13")))).andExpect(status().isCreated()).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(patch("/api/admin/bookings/"+id).with(user("admin").roles("OWNER")).with(csrf()).contentType("application/json")
                .content("{\"status\":\"confirmed\"}")).andExpect(status().isOk());
        var update=house("friends");update.putArray("blockedDates").addObject().put("start","2099-10-11").put("end","2099-10-14");
        mvc.perform(put("/api/admin/houses/friends").with(user("admin").roles("OWNER")).with(csrf()).contentType("application/json").content(update.toString()))
                .andExpect(status().isConflict());
        assertEquals(0,house("friends").get("blockedDates").size());
    }
}
