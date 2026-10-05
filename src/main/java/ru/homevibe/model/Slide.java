package ru.homevibe.model;

import jakarta.validation.constraints.*;

/** Порядок слайдов определяется их положением в списке. */
public record Slide(@NotBlank @Size(max=100) String title,
                    @NotBlank @Size(max=350) String description,
                    @NotBlank @Size(max=80) String caption,
                    @NotBlank @Size(max=100) String imageUrl,
                    @NotNull @Size(max=40) String houseId) {}
