# External API 示例

## 1. cURL 命令

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/external/model-jobs" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleId": 1,
    "timeRange": {
      "start": "2026-03-01 00:00:00",
      "end": "2026-03-01 01:00:00"
    }
  }'
```

```bash
curl "http://127.0.0.1:8080/api/v1/external/jobs/{jobId}"
curl "http://127.0.0.1:8080/api/v1/external/jobs/{jobId}/result"
```

## 2. Java 示例

运行 `examples/external/java/ExternalApiJavaExample.java`，示例会执行：

1. 提交模型作业
2. 轮询到完成
3. 输出最终结果

## 3. Python 示例

```bash
python examples/external/python/external_api_example.py
```
