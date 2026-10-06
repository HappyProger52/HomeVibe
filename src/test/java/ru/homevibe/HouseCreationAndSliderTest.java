package ru.homevibe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.homevibe.service.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties="homevibe.owner-password=Test-only-password-123")
@AutoConfigureMockMvc
class HouseCreationAndSliderTest {
    static final Path DATA;
    static {try {DATA=Files.createTempDirectory("homevibe-slider-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        for(String name:List.of("bookings","customers","houses","reviews","slides"))r.add("homevibe."+name+"-file",()->DATA.resolve(name+".json").toString());
        r.add("homevibe.uploads-directory",()->DATA.resolve("uploads").toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired HouseService houses;
    @Autowired ImageService images;
    ObjectNode house() throws Exception {
        var response=mvc.perform(get("/api/homes")).andReturn();
        var body=(ObjectNode)mapper.readTree(response.getResponse().getContentAsString()).get("homes").get(0);
        body.put("name","Новый большой дом");body.put("guests",12);return body;
    }
    @Test void ownerCreatesBookableHouseWithoutReplacingExistingAndItSurvivesReload() throws Exception {
        var body=house();
        var response=mvc.perform(post("/api/admin/houses").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body.toString()))
                .andExpect(status().isCreated()).andReturn();
        String id=mapper.readTree(response.getResponse().getContentAsString()).get("id").asText();
        assertNotEquals("forest",id);assertEquals("Лесной дом",houses.findById("forest").name());
        var reloaded=new HouseService(mapper,images,DATA.resolve("houses.json").toString());
        assertEquals(12,reloaded.findById(id).guests());
        mvc.perform(post("/api/bookings").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("homeId",id,"start","2099-10-10","end","2099-10-12","guests",12,"name","Гость","phone","+79990000000","note","","requestKey",UUID.randomUUID()))))
                .andExpect(status().isCreated());
        body.put("name","Изменённый новый дом");
        mvc.perform(put("/api/admin/houses/"+id).with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Изменённый новый дом"));
    }
    @Test void creationRequiresOwnerCsrfAndValidHouse() throws Exception {
        var body=house();int count=houses.findAll().size();
        mvc.perform(post("/api/admin/houses").with(csrf()).contentType("application/json").content(body.toString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/houses").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json").content(body.toString())).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/houses").with(user("owner").roles("OWNER")).contentType("application/json").content(body.toString())).andExpect(status().isForbidden());
        for(String field:List.of("name","imageUrl")) {
            var invalid=body.deepCopy();invalid.put(field,field.equals("name")?"   ":"https://example.com/photo.png");
            mvc.perform(post("/api/admin/houses").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(invalid.toString())).andExpect(status().isBadRequest());
        }
        body.put("availableFrom","2099-12-10");body.put("availableUntil","2099-12-01");
        mvc.perform(post("/api/admin/houses").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        assertEquals(count,houses.findAll().size());
    }
    @Test void sliderOrderTextsAndLinksPersistAndInvalidUpdatesDoNotOverwrite() throws Exception {
        var first=Map.of("title","Первый <script>","description","Описание","caption","Подпись","imageUrl","/cabin.svg","houseId","lake");
        var second=Map.of("title","Второй","description","Описание","caption","Подпись","imageUrl","/cabin.svg","houseId","");
        String body=mapper.writeValueAsString(Map.of("slides",List.of(first,second)));
        mvc.perform(put("/api/admin/slides").with(user("guest").roles("CUSTOMER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/slides").with(user("owner").roles("OWNER")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/slides").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/slides")).andExpect(jsonPath("$.slides",hasSize(2))).andExpect(jsonPath("$.slides[0].houseId").value("lake"));
        var reloaded=new SliderService(mapper,images,houses,DATA.resolve("slides.json").toString());
        assertEquals("Первый <script>",reloaded.all().getFirst().title());
        for(String invalid:List.of("{\"slides\":[]}","{\"slides\":[null]}",body.replace("/cabin.svg","javascript:bad"),body.replace("Первый <script>"," "),mapper.writeValueAsString(Map.of("slides",Collections.nCopies(11,first)))))
            mvc.perform(put("/api/admin/slides").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/slides").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(body.replace("lake","missing"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/slides")).andExpect(jsonPath("$.slides",hasSize(2))).andExpect(jsonPath("$.slides[0].houseId").value("lake"));
        mvc.perform(put("/api/admin/slides").with(user("owner").roles("OWNER")).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("slides",List.of(second))))).andExpect(status().isOk());
        mvc.perform(get("/api/slides")).andExpect(jsonPath("$.slides",hasSize(1))).andExpect(jsonPath("$.slides[0].title").value("Второй"));
    }
}
