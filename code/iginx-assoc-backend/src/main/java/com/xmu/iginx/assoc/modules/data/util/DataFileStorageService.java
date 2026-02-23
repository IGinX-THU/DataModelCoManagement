package com.xmu.iginx.assoc.modules.data.util;

import com.xmu.iginx.assoc.framework.storage.StorageConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataFileStorageService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final StorageConfig storageConfig;

    public StoredFile createFile(String prefix, String suffix) {
        String fileName = prefix + "_" + LocalDateTime.now().format(FORMATTER) + "_" + UUID.randomUUID() + suffix;
        Path root = resolveRoot();
        try {
            Files.createDirectories(root);
        } catch (Exception ignored) {
        }
        return new StoredFile(fileName, root.resolve(fileName));
    }

    public Path resolveFile(String fileName) {
        Path root = resolveRoot();
        return root.resolve(fileName).normalize().toAbsolutePath();
    }

    public Path resolveRoot() {
        Path root = Paths.get(storageConfig.getDataRoot());
        if (!root.isAbsolute()) {
            root = Paths.get(System.getProperty("user.dir")).resolve(root);
        }
        return root.normalize().toAbsolutePath();
    }

    public record StoredFile(String fileName, Path path) {
    }
}
