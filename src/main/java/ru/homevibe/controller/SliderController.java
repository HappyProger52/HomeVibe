package ru.homevibe.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import ru.homevibe.model.Slide;
import ru.homevibe.service.SliderService;
import java.util.*;

@RestController
public class SliderController {
    private final SliderService slider;
    public SliderController(SliderService slider){this.slider=slider;}
    public record Update(@NotNull @Size(min=1,max=10) List<@NotNull @Valid Slide> slides) {}
    @GetMapping("/api/slides") public Map<String,?> all(){return Map.of("slides",slider.all());}
    @PutMapping("/api/admin/slides") public Map<String,?> save(@Valid @RequestBody Update request){return Map.of("slides",slider.save(request.slides()));}
}
