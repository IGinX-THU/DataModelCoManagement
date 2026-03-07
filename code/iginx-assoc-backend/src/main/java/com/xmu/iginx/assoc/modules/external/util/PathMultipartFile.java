package com.xmu.iginx.assoc.modules.external.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathMultipartFile implements MultipartFile {

    private final Path path;
    private final String originalFilename;
    private final String contentType;

    public PathMultipartFile(Path path, String originalFilename, String contentType) {
        this.path = path;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return originalFilename;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        try {
            return !Files.exists(path) || Files.size(path) <= 0;
        } catch (IOException ex) {
            return true;
        }
    }

    @Override
    public long getSize() {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException ex) {
            return 0L;
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        if (dest == null) {
            throw new IllegalStateException("目标文件不能为空");
        }
        Files.copy(path, dest.toPath());
    }
}
