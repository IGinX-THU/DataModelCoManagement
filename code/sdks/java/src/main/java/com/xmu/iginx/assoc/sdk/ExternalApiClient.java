package com.xmu.iginx.assoc.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class ExternalApiClient {

    private final String baseUrl;
    private final String token;
    private final int retries;
    private final boolean enableLog;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExternalApiClient(String baseUrl,
                             String token,
                             Duration timeout,
                             int retries,
                             boolean enableLog) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.token = token;
        this.retries = Math.max(0, retries);
        this.enableLog = enableLog;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
    }

    public JsonNode submitModelJob(Map<String, Object> request) {
        return postJson("/api/v1/external/model-jobs", request);
    }

    public JsonNode submitAlgorithmJob(Map<String, Object> request) {
        return postJson("/api/v1/external/algorithm-jobs", request);
    }

    public JsonNode submitDataExportJob(Map<String, Object> request) {
        return postJson("/api/v1/external/data-export-jobs", request);
    }

    public JsonNode submitDataImportJob(Map<String, Object> request, Path filePath) {
        try {
            String boundary = "----iginx-boundary-" + UUID.randomUUID();
            byte[] body = buildMultipartBody(boundary, request, filePath);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/external/data-import-jobs"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            addAuthHeader(builder);
            JsonNode envelope = sendWithRetry(builder.build());
            return unwrapData(envelope);
        } catch (IOException ex) {
            throw new ApiException(500, "构建导入请求失败: " + ex.getMessage());
        }
    }

    public JsonNode getJobStatus(String jobId) {
        return getJson("/api/v1/external/jobs/" + jobId);
    }

    public JsonNode getJobResult(String jobId) {
        return getJson("/api/v1/external/jobs/" + jobId + "/result");
    }

    public JsonNode waitForFinish(String jobId, Duration pollInterval, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() <= deadline) {
            JsonNode statusNode = getJobStatus(jobId);
            String status = statusNode.path("status").asText("");
            if ("SUCCEEDED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status)) {
                return getJobResult(jobId);
            }
            sleepQuietly(pollInterval);
        }
        throw new ApiException(408, "等待任务完成超时: " + jobId);
    }

    private JsonNode postJson(String path, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            addAuthHeader(builder);
            JsonNode envelope = sendWithRetry(builder.build());
            return unwrapData(envelope);
        } catch (IOException ex) {
            throw new ApiException(500, "请求序列化失败: " + ex.getMessage());
        }
    }

    private JsonNode getJson(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofMinutes(3))
            .GET();
        addAuthHeader(builder);
        JsonNode envelope = sendWithRetry(builder.build());
        return unwrapData(envelope);
    }

    private JsonNode sendWithRetry(HttpRequest request) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                if (enableLog) {
                    System.out.printf("[ExternalApiClient] %s %s (attempt=%d)%n",
                        request.method(), request.uri(), attempt);
                }
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int statusCode = response.statusCode();
                if (statusCode >= 500 && attempt <= retries + 1) {
                    sleepQuietly(Duration.ofMillis(200L * attempt));
                    continue;
                }
                JsonNode envelope = objectMapper.readTree(response.body());
                int code = envelope.path("code").asInt(statusCode);
                if (code != 200) {
                    throw new ApiException(code, envelope.path("msg").asText("调用失败"));
                }
                return envelope;
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                if (attempt <= retries + 1) {
                    sleepQuietly(Duration.ofMillis(200L * attempt));
                    continue;
                }
                throw new ApiException(500, "请求失败: " + ex.getMessage());
            }
        }
    }

    private JsonNode unwrapData(JsonNode envelope) {
        JsonNode data = envelope.get("data");
        return data == null ? objectMapper.createObjectNode() : data;
    }

    private void addAuthHeader(HttpRequest.Builder builder) {
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
    }

    private byte[] buildMultipartBody(String boundary, Map<String, Object> request, Path filePath) throws IOException {
        String requestJson = objectMapper.writeValueAsString(request);
        String fileName = filePath.getFileName().toString();
        byte[] fileBytes = Files.readAllBytes(filePath);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        writeString(outputStream, "--" + boundary + "\r\n");
        writeString(outputStream, "Content-Disposition: form-data; name=\"request\"\r\n");
        writeString(outputStream, "Content-Type: application/json; charset=UTF-8\r\n\r\n");
        writeString(outputStream, requestJson + "\r\n");

        writeString(outputStream, "--" + boundary + "\r\n");
        writeString(outputStream, "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        writeString(outputStream, "Content-Type: application/octet-stream\r\n\r\n");
        outputStream.write(fileBytes);
        writeString(outputStream, "\r\n");

        writeString(outputStream, "--" + boundary + "--\r\n");
        return outputStream.toByteArray();
    }

    private void writeString(ByteArrayOutputStream outputStream, String text) throws IOException {
        outputStream.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(499, "线程被中断");
        }
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            return "http://127.0.0.1:8080";
        }
        String trimmed = rawBaseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
