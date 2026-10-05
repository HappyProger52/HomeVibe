package ru.homevibe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Пример Map: число попыток входа по адресу клиента за 15 минут. */
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private record Attempts(int count, long expiresAt) {}
    private final Map<String, Attempts> attempts = new HashMap<>();
    private final ObjectMapper mapper;

    public LoginRateLimitFilter(ObjectMapper mapper) { this.mapper = mapper; }

    private synchronized boolean allowed(String address) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        Attempts previous = attempts.getOrDefault(address, new Attempts(0, now + 900_000));
        attempts.put(address, new Attempts(previous.count() + 1, previous.expiresAt()));
        return previous.count() < 10;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ((request.getServletPath().equals("/api/login") || request.getServletPath().equals("/api/register")) && request.getMethod().equals("POST")
                && !allowed(request.getRemoteAddr())) {
            SecurityConfig.write(mapper, response, 429, Map.of("error", "Слишком много попыток. Попробуйте через 15 минут."));
            return;
        }
        chain.doFilter(request, response);
    }
}
