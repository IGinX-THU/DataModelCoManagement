# SDK 生成与封装说明

本目录包含两部分内容：

- `openapi/`：OpenAPI 自动生成配置与命令脚本
- `java/`、`python/`：面向业务方的薄封装 SDK（统一错误处理、重试、轮询）

## 1. 从 OpenAPI 自动生成基础客户端

> OpenAPI 契约源：`/iginx-assoc-backend/docs/openapi.yaml`

执行：

```powershell
powershell -ExecutionPolicy Bypass -File "./openapi/generate-sdk.ps1"
```

生成输出：

- Java 基础客户端：`/sdks/openapi/generated/java`
- Python 基础客户端：`/sdks/openapi/generated/python`

## 2. 薄封装 SDK

- Java：`/sdks/java`
  - `ExternalApiClient`：`submit_xxx_job`、`get_job_status`、`get_job_result`、`wait_for_finish`
- Python：`/sdks/python`
  - `ExternalApiClient`：`submit_xxx_job`、`get_job_status`、`get_job_result`、`wait_for_finish`

## 3. 快速示例

- Java 示例：`/examples/external/java/ExternalApiJavaExample.java`
- Python 示例：`/examples/external/python/external_api_example.py`
