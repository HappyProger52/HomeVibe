package ru.homevibe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.homevibe.service.CustomerService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public UserDetailsService users(PasswordEncoder encoder, CustomerService customers,
                                    @Value("${homevibe.owner-login:owner}") String login,
                                    @Value("${homevibe.owner-password}") String password) {
        if (!password.isBlank() && (password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)) {
            throw new IllegalArgumentException("Пароль владельца: от 12 символов и до 72 байт UTF-8.");
        }
        String ownerLogin = CustomerService.normalize(login);
        if (!ownerLogin.matches("[a-z0-9._-]{3,40}")) throw new IllegalArgumentException("Некорректный логин администратора");
        var owner = User.withUsername(ownerLogin)
                .password(encoder.encode(password.isBlank() ? UUID.randomUUID().toString() : password))
                .roles("OWNER").disabled(password.isBlank()).build();
        return username -> {
            String normalized = CustomerService.normalize(username);
            // Spring стирает пароль у возвращённого User после входа: каждый раз нужна копия.
            if (normalized.equals(ownerLogin)) return User.withUserDetails(owner).build();
            var customer = customers.find(normalized).orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
            return User.withUsername(customer.username()).password(customer.passwordHash()).roles("CUSTOMER").build();
        };
    }

    @Bean
    public SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.authorizeHttpRequests(rules -> rules
                        .requestMatchers("/api/admin/**").hasRole("OWNER")
                        .requestMatchers("/api/account/**", "/api/bookings").hasRole("CUSTOMER")
                        .requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/cabin.svg", "/favicon.ico",
                                "/admin", "/admin/", "/admin.html", "/admin.js", "/account", "/account/", "/account.html", "/account.js", "/common.js", "/uploads/**",
                                "/slider.js", "/slider-editor.js", "/api/slides",
                                "/api/csrf", "/api/session", "/api/homes", "/api/reviews", "/api/register", "/api/login", "/error").permitAll()
                        .anyRequest().denyAll())
                // CSRF оставлен включённым. Фронтенд получает токен через /api/csrf.
                .formLogin(login -> login.loginProcessingUrl("/api/login")
                        .successHandler((request, response, auth) -> write(mapper, response, 200, Map.of("ok", true)))
                        .failureHandler((request, response, error) -> write(mapper, response, 401, Map.of("error", "Неверный пароль или вход ещё не настроен."))))
                .logout(logout -> logout.logoutUrl("/api/logout").deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, auth) -> write(mapper, response, 200, Map.of("ok", true))))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> write(mapper, response, 401, Map.of("error", "Войдите в свой аккаунт.")))
                        .accessDeniedHandler((request, response, error) -> write(mapper, response, 403, Map.of("error", "Доступ запрещён. Обновите страницу и повторите попытку."))))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")))
                .addFilterBefore(new LoginRateLimitFilter(mapper), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static void write(ObjectMapper mapper, HttpServletResponse response, int status, Map<String, ?> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), body);
    }
}
