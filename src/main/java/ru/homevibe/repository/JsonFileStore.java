package ru.homevibe.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.homevibe.exception.StorageException;
import java.io.IOException;
import java.nio.file.*;

/** Общая запись через временный файл: при ошибке старый файл остаётся целым. */
public final class JsonFileStore {
    private JsonFileStore() {}

    public static <T> T read(ObjectMapper mapper, Path path, TypeReference<T> type, T fallback) {
        if (!Files.exists(path)) return fallback;
        try {
            T result = mapper.readValue(path.toFile(), type);
            if (result == null) throw new IOException("Empty data");
            return result;
        } catch (IOException e) { throw new StorageException("Не удалось прочитать " + path, e); }
    }

    public static void write(ObjectMapper mapper, Path file, Object value) {
        Path temporary = null;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "homevibe-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new StorageException("Не удалось сохранить данные", e); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }
}
