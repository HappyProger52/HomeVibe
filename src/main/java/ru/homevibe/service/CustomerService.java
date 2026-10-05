package ru.homevibe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.homevibe.exception.BookingException;
import ru.homevibe.repository.JsonFileStore;
import java.nio.file.Path;
import java.util.*;

@Service
public class CustomerService {
    public record Customer(String username, String name, String passwordHash) {}
    private final Map<String, Customer> customers = new LinkedHashMap<>();
    private final ObjectMapper mapper;
    private final PasswordEncoder encoder;
    private final Path file;
    private final String ownerLogin;

    public CustomerService(ObjectMapper mapper, PasswordEncoder encoder,
                           @Value("${homevibe.customers-file:data/customers.json}") String filename,
                           @Value("${homevibe.owner-login:owner}") String ownerLogin) {
        this.mapper = mapper;
        this.encoder = encoder;
        this.file = Path.of(filename).toAbsolutePath();
        this.ownerLogin = normalize(ownerLogin);
        List<Customer> stored = JsonFileStore.read(mapper, file, new TypeReference<>() {}, List.of());
        for (Customer customer : stored) {
            if (customer == null || customer.username() == null || customer.passwordHash() == null
                    || customer.name() == null || customers.putIfAbsent(customer.username(), customer) != null) {
                throw new IllegalStateException("Повреждён файл пользователей");
            }
        }
        if (customers.containsKey(this.ownerLogin)) throw new IllegalStateException("Логин администратора занят клиентом. Выберите другой HOMEVIBE_OWNER_LOGIN.");
    }

    public static String normalize(String username) { return username.trim().toLowerCase(Locale.ROOT); }
    public synchronized Optional<Customer> find(String username) { return Optional.ofNullable(customers.get(normalize(username))); }

    public synchronized void register(String login, String name, String password) {
        String username = normalize(login);
        if (!username.matches("[a-z0-9._-]{3,40}") || name.trim().length() < 2 || name.length() > 80
                || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BookingException(HttpStatus.BAD_REQUEST, "Логин: 3–40 латинских символов, цифр, точек, _ или -. Пароль: от 12 символов, до 72 байт. Укажите имя.");
        }
        if (username.equals(ownerLogin) || customers.containsKey(username)) {
            throw new BookingException(HttpStatus.CONFLICT, "Этот логин уже занят.");
        }
        Map<String, Customer> updated = new LinkedHashMap<>(customers);
        updated.put(username, new Customer(username, name.trim(), encoder.encode(password)));
        JsonFileStore.write(mapper, file, updated.values());
        customers.clear();
        customers.putAll(updated);
    }
}
