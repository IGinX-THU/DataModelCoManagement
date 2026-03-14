# Python External SDK

## 安装

```bash
cd sdks/python
pip install -e .
```

## 快速使用

```python
from iginx_external_sdk import ExternalApiClient

client = ExternalApiClient(
    base_url="http://127.0.0.1:8080",
    token="",
    timeout=10,
    retries=2,
    enable_log=True,
)

create_resp = client.submit_model_job({
    "ruleId": 1,
    "timeRange": {
        "start": "2026-03-01 00:00:00",
        "end": "2026-03-01 01:00:00",
    },
})
job_id = create_resp["jobId"]
result = client.wait_for_finish(job_id, poll_interval=1, timeout=600)
print(result)
```
