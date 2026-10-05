package ru.homevibe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.homevibe.exception.BookingException;
import ru.homevibe.exception.StorageException;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class ImageService {
    private final Path directory;
    public ImageService(@Value("${homevibe.uploads-directory:data/uploads}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath();
    }

    public Path path(String filename) {
        if (!filename.matches("[a-f0-9-]{36}\\.png")) throw new BookingException(HttpStatus.NOT_FOUND, "Фото не найдено.");
        Path path = directory.resolve(filename);
        if (!Files.isRegularFile(path)) throw new BookingException(HttpStatus.NOT_FOUND, "Фото не найдено.");
        return path;
    }

    public void validateUrl(String url) {
        if ("/cabin.svg".equals(url)) return;
        if (!url.startsWith("/uploads/")) throw new BookingException(HttpStatus.BAD_REQUEST, "Загрузите фотографию через форму.");
        path(url.substring("/uploads/".length()));
    }

    public String upload(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new BookingException(HttpStatus.BAD_REQUEST, "Размер фото — до 5 МБ.");
        try (var source = file.getInputStream(); var input = ImageIO.createImageInputStream(source)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg"))
                        || (long)reader.getWidth(0) * reader.getHeight(0) > 20_000_000) throw invalid();
                var image = reader.read(0);
                Files.createDirectories(directory);
                String filename = UUID.randomUUID() + ".png";
                // Перекодирование удаляет метаданные и исключает выдачу SVG/HTML под видом картинки.
                ImageIO.write(image, "png", directory.resolve(filename).toFile());
                return "/uploads/" + filename;
            } finally { reader.dispose(); }
        } catch (javax.imageio.IIOException e) { throw invalid(); }
        catch (IOException e) { throw new StorageException("Не удалось сохранить фото", e); }
    }

    private BookingException invalid() { return new BookingException(HttpStatus.BAD_REQUEST, "Выберите JPEG или PNG, не более 20 мегапикселей."); }
}
