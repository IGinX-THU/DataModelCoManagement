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

/**
 * 文件存储服务，负责生成与解析文件路径。
 */
@Component
@RequiredArgsConstructor
public class DataFileStorageService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final StorageConfig storageConfig;

    /**
     * 创建存储文件元信息并确保目录存在。
     *
     * @param prefix 文件名前缀
     * @param suffix 文件后缀
     * @return 存储文件信息
     */
    public StoredFile createFile(String prefix, String suffix) {
        String fileName = prefix + "_" + LocalDateTime.now().format(FORMATTER) + "_" + UUID.randomUUID() + suffix;
        Path root = resolveRoot();
        try {
            // 确保导出根目录存在
            Files.createDirectories(root);
        } catch (Exception ignored) {
        }
        return new StoredFile(fileName, root.resolve(fileName));
    }

    /**
     * 解析指定文件名的绝对路径。
     *
     * @param fileName 文件名
     * @return 绝对路径
     */
    public Path resolveFile(String fileName) {
        Path root = resolveRoot();
        return root.resolve(fileName).normalize().toAbsolutePath();
    }

    /**
     * 解析导出根目录路径。
     *
     * @return 根目录路径
     */
    public Path resolveRoot() {
        Path root = Paths.get(storageConfig.getDataRoot());
        if (!root.isAbsolute()) {
            // 相对路径默认以应用工作目录为基准
            root = Paths.get(System.getProperty("user.dir")).resolve(root);
        }
        return root.normalize().toAbsolutePath();
    }

    /**
     * 存储文件描述信息。
     *
     * @param fileName 文件名
     * @param path 文件路径
     */
    public record StoredFile(String fileName, Path path) {
    }
}
