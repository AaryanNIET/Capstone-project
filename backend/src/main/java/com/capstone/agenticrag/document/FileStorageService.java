package com.capstone.agenticrag.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.capstone.agenticrag.config.AppProperties;

/**
 * Stores original uploads on the local filesystem (a Docker volume in deployment) as {@code <userId>/<documentId>.bin}.
 * Paths are always derived from UUIDs, never from user-supplied filenames, which rules out path traversal.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path root;

    public FileStorageService(AppProperties props) {
        this.root = Path.of(props.storage().directory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + root, e);
        }
    }

    public String store(UUID userId, UUID documentId, byte[] data) {
        String relative = userId + "/" + documentId + ".bin";
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "upload", ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }
        return relative;
    }

    public byte[] read(String relativePath) throws IOException {
        return Files.readAllBytes(resolve(relativePath));
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", relativePath);
        }
    }

    public void deleteUserFiles(UUID userId) {
        Path dir = resolve(userId.toString());
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("Could not delete files for user {}", userId);
        }
    }

    private Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return p;
    }
}
