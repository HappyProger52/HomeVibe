package ru.homevibe.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import ru.homevibe.service.CustomerService;
import java.util.Map;

@RestController
public class AccountController {
    public record Registration(@NotBlank @Size(max=40) String username,
                               @NotBlank @Size(max=80) String name,
                               @NotBlank @Size(min=12,max=72) String password) {}
    private final CustomerService customers;
    public AccountController(CustomerService customers) { this.customers = customers; }

    @PostMapping("/api/register")
    public Map<String, Boolean> register(@Valid @RequestBody Registration registration) {
        customers.register(registration.username(), registration.name(), registration.password());
        return Map.of("ok", true);
    }
}
