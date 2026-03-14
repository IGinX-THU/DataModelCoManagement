import json
import time
from pathlib import Path
from typing import Any

import requests


class ApiException(Exception):
    def __init__(self, code: int, message: str) -> None:
        super().__init__(message)
        self.code = code


class ExternalApiClient:
    def __init__(
        self,
        base_url: str = "http://127.0.0.1:8080",
        token: str = "",
        timeout: int = 10,
        retries: int = 2,
        enable_log: bool = False,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.retries = max(0, retries)
        self.enable_log = enable_log
        self.session = requests.Session()
        if token:
            self.session.headers.update({"Authorization": f"Bearer {token}"})

    def submit_model_job(self, request: dict[str, Any]) -> dict[str, Any]:
        return self._post_json("/api/v1/external/model-jobs", request)

    def submit_algorithm_job(self, request: dict[str, Any]) -> dict[str, Any]:
        return self._post_json("/api/v1/external/algorithm-jobs", request)

    def submit_data_export_job(self, request: dict[str, Any]) -> dict[str, Any]:
        return self._post_json("/api/v1/external/data-export-jobs", request)

    def submit_data_import_job(self, request: dict[str, Any], file_path: str | Path) -> dict[str, Any]:
        path = Path(file_path)
        if not path.exists():
            raise ApiException(400, f"文件不存在: {path}")
        with path.open("rb") as stream:
            files = {
                "request": (None, json.dumps(request), "application/json"),
                "file": (path.name, stream, "application/octet-stream"),
            }
            return self._request("POST", "/api/v1/external/data-import-jobs", files=files)

    def get_job_status(self, job_id: str) -> dict[str, Any]:
        return self._request("GET", f"/api/v1/external/jobs/{job_id}")

    def get_job_result(self, job_id: str) -> dict[str, Any]:
        return self._request("GET", f"/api/v1/external/jobs/{job_id}/result")

    def wait_for_finish(
        self,
        job_id: str,
        poll_interval: int = 1,
        timeout: int = 600,
    ) -> dict[str, Any]:
        deadline = time.time() + timeout
        while time.time() <= deadline:
            status_data = self.get_job_status(job_id)
            status = str(status_data.get("status", "")).upper()
            if status in {"SUCCEEDED", "FAILED", "CANCELED"}:
                return self.get_job_result(job_id)
            time.sleep(max(1, poll_interval))
        raise ApiException(408, f"等待任务完成超时: {job_id}")

    def _post_json(self, path: str, body: dict[str, Any]) -> dict[str, Any]:
        return self._request("POST", path, json=body)

    def _request(self, method: str, path: str, **kwargs: Any) -> dict[str, Any]:
        url = f"{self.base_url}{path}"
        for attempt in range(1, self.retries + 3):
            try:
                if self.enable_log:
                    print(f"[ExternalApiClient] {method} {url} (attempt={attempt})")
                response = self.session.request(
                    method=method,
                    url=url,
                    timeout=self.timeout,
                    **kwargs,
                )
                if response.status_code >= 500 and attempt <= self.retries + 1:
                    time.sleep(0.2 * attempt)
                    continue
                payload = response.json()
                code = int(payload.get("code", response.status_code))
                if code != 200:
                    raise ApiException(code, str(payload.get("msg", "调用失败")))
                data = payload.get("data")
                return {} if data is None else data
            except ApiException:
                raise
            except Exception as exc:
                if attempt <= self.retries + 1:
                    time.sleep(0.2 * attempt)
                    continue
                raise ApiException(500, f"请求失败: {exc}") from exc
        raise ApiException(500, "请求失败")
