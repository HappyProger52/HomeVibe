package ru.homevibe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import ru.homevibe.exception.BookingException;
import ru.homevibe.model.Slide;
import ru.homevibe.repository.JsonFileStore;
import java.nio.file.Path;
import java.util.List;

@Service
public class SliderService {
    private final ObjectMapper mapper;
    private final ImageService images;
    private final HouseService houses;
    private final Path file;
    private List<Slide> slides;
    public SliderService(ObjectMapper mapper,ImageService images,HouseService houses,
                         @Value("${homevibe.slides-file:data/slides.json}") String filename) {
        this.mapper=mapper;this.images=images;this.houses=houses;this.file=Path.of(filename);
        slides=List.copyOf(JsonFileStore.read(mapper,file,new TypeReference<List<Slide>>() {},List.of(
            new Slide("Ближе к природе. Ближе к себе.","Уютные дома, тихие вечера и время для того, что действительно важно. Найдите своё место.","Лесной дом","/cabin.svg","forest"),
            new Slide("Утро, которое можно не торопить.","Завтрак для двоих, прогулка у воды и целый день без городского шума.","Тихий берег","/cabin.svg","lake"),
            new Slide("Больше места для тёплых встреч.","Соберите близких под одной крышей. Просторный дом, лесная поляна и вечера вместе.","Большая поляна","/cabin.svg","friends")
        )));
        if(slides.isEmpty() || slides.size()>10)throw new IllegalStateException("Некорректный файл слайдера");
    }
    public synchronized List<Slide> all() {return slides;}
    public synchronized List<Slide> save(List<Slide> requested) {
        if(requested.isEmpty()||requested.size()>10)throw new BookingException(HttpStatus.BAD_REQUEST,"Добавьте от 1 до 10 слайдов.");
        for(Slide s:requested) {
            images.validateUrl(s.imageUrl());
            if(!s.houseId().isBlank())houses.findById(s.houseId());
        }
        List<Slide> updated=requested.stream().map(s->new Slide(s.title().trim(),s.description().trim(),s.caption().trim(),s.imageUrl(),s.houseId())).toList();
        JsonFileStore.write(mapper,file,updated);slides=updated;return slides;
    }
}
