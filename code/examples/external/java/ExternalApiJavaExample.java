import com.fasterxml.jackson.databind.JsonNode;
import com.xmu.iginx.assoc.sdk.ExternalApiClient;

import java.time.Duration;
import java.util.Map;

public class ExternalApiJavaExample {

    public static void main(String[] args) {
        ExternalApiClient client = new ExternalApiClient(
            "http://127.0.0.1:8080",
            "",
            Duration.ofSeconds(10),
            2,
            true
        );

        JsonNode createResult = client.submitModelJob(Map.of(
            "ruleId", 1,
            "timeRange", Map.of(
                "start", "2026-03-01 00:00:00",
                "end", "2026-03-01 01:00:00"
            ),
            "pollIntervalSeconds", 1,
            "timeoutSeconds", 600
        ));
        String jobId = createResult.path("jobId").asText();
        JsonNode finalResult = client.waitForFinish(jobId, Duration.ofSeconds(1), Duration.ofMinutes(10));
        System.out.println("作业结果: " + finalResult.toPrettyString());
    }
}
