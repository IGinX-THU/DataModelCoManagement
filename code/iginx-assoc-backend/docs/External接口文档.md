# External 对外接口文档

## 1. 接口概览

对外异步作业接口统一挂载在 `/api/v1/external` 下，覆盖四类能力：

- 模型调用：`POST /api/v1/external/model-jobs`
- 算法调用：`POST /api/v1/external/algorithm-jobs`
- 数据导入：`POST /api/v1/external/data-import-jobs`
- 数据导出：`POST /api/v1/external/data-export-jobs`
- 作业状态：`GET /api/v1/external/jobs/{jobId}`
- 作业结果：`GET /api/v1/external/jobs/{jobId}/result`

统一状态：

- `PENDING`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `CANCELED`

统一错误对象：`ExternalErrorResponse`

- `code`：错误码（例如 `INVALID_ARGUMENT` / `EXECUTION_FAILED` / `TIMEOUT`）
- `message`：错误说明
- `traceId`：请求追踪 ID

---

## 2. 端到端调用链示例（cURL）

### 2.1 提交导入任务

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/external/data-import-jobs" \
  -H "Accept: application/json" \
  -F 'request={"importType":"TIME_SERIES","timeSeriesRequest":{"sourceId":1,"storageGroup":"demo.flow","timestampColumn":"timestamp"}};type=application/json' \
  -F "file=@../examples/timeseries/ts_example.csv"
```

### 2.2 提交模型调用任务

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/external/model-jobs" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleId": 1,
    "timeRange": {
      "start": "2026-03-01 00:00:00",
      "end": "2026-03-01 01:00:00"
    },
    "pollIntervalSeconds": 1,
    "timeoutSeconds": 600
  }'
```

### 2.3 提交算法调用任务

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/external/algorithm-jobs" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "TASK_COMPARE",
    "taskIds": ["taskA", "taskB"],
    "mode": "absolute"
  }'
```

### 2.4 提交导出任务

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/external/data-export-jobs" \
  -H "Content-Type: application/json" \
  -d '{
    "exportRequest": {
      "type": "STRUCTURED",
      "sourceId": 2,
      "format": "CSV",
      "schema": "demo",
      "table": "flow"
    }
  }'
```

### 2.5 轮询状态与结果

```bash
curl "http://127.0.0.1:8080/api/v1/external/jobs/{jobId}"
curl "http://127.0.0.1:8080/api/v1/external/jobs/{jobId}/result"
```

---

## 3. 命令说明

- `model-jobs`：将规则执行映射到原有任务引擎，默认轮询直到内部任务结束。
- `algorithm-jobs`：支持 `TASK_SERIES`、`TASK_COMPARE`、`TASK_EXPORT`、`TASK_REPORT` 四种动作。
- `data-import-jobs`：使用 `multipart/form-data`，`request` 为 JSON，`file` 为导入文件。
- `data-export-jobs`：导出请求通过 `exportRequest` 传入，并返回下载地址。
- `jobs/{jobId}`：用于查询作业生命周期状态。
- `jobs/{jobId}/result`：用于获取作业最终结果、下载链接和错误信息。

---

## 4. Java / Python 示例入口

- Java 示例：`/examples/external/java/ExternalApiJavaExample.java`
- Python 示例：`/examples/external/python/external_api_example.py`
- Java SDK：`/sdks/java`
- Python SDK：`/sdks/python`
