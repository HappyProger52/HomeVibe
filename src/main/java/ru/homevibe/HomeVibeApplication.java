package ru.homevibe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class HomeVibeApplication {
    public static void main(String[] args) throws IOException {
        // JDK 21 on Windows can fail with a Cyrillic system temp path.
        // Keep its internal socket files in the project, without changing OS settings.
        if (System.getProperty("os.name").startsWith("Windows")
                && System.getProperty("jdk.net.unixdomain.tmpdir") == null) {
            Path socketDirectory = Files.createDirectories(Path.of("target", "sockets")).toAbsolutePath();
            System.setProperty("jdk.net.unixdomain.tmpdir", socketDirectory.toString());
        }
        SpringApplication.run(HomeVibeApplication.class, args);
    }
}
