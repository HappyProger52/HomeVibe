package ru.homevibe.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import ru.homevibe.service.CustomerService;

@RestController
public class SessionController {
    private final boolean configured;
    private final CustomerService customers;

    public SessionController(@Value("${homevibe.owner-password}") String password, CustomerService customers) {
        this.configured = !password.isBlank();
        this.customers = customers;
    }

    @GetMapping("/api/session")
    public Map<String, ?> session(Authentication authentication) {
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        String role = authenticated && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OWNER")) ? "OWNER" : "CUSTOMER";
        String username = authenticated ? authentication.getName() : "";
        String name = authenticated && role.equals("CUSTOMER") ? customers.find(username).map(CustomerService.Customer::name).orElse(username) : username;
        return Map.of("authenticated", authenticated, "configured", configured, "role", authenticated ? role : "", "username", username, "name", name);
    }

    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
}
