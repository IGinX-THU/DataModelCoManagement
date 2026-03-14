# Java External SDK

## 安装

```bash
cd sdks/java
mvn -DskipTests install
```

## 快速使用

```java
ExternalApiClient client = new ExternalApiClient(
    "http://127.0.0.1:8080",
    "",
    Duration.ofSeconds(10),
    2,
    true
);

JsonNode createResp = client.submitModelJob(Map.of(
    "ruleId", 1,
    "timeRange", Map.of(
        "start", "2026-03-01 00:00:00",
        "end", "2026-03-01 01:00:00"
    )
));
String jobId = createResp.path("jobId").asText();
JsonNode result = client.waitForFinish(jobId, Duration.ofSeconds(1), Duration.ofMinutes(10));
```
