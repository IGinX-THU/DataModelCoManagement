from iginx_external_sdk import ExternalApiClient


def main() -> None:
    client = ExternalApiClient(
        base_url="http://127.0.0.1:8080",
        token="",
        timeout=10,
        retries=2,
        enable_log=True,
    )

    create_result = client.submit_model_job(
        {
            "ruleId": 1,
            "timeRange": {
                "start": "2026-03-01 00:00:00",
                "end": "2026-03-01 01:00:00",
            },
            "pollIntervalSeconds": 1,
            "timeoutSeconds": 600,
        }
    )
    job_id = create_result["jobId"]
    final_result = client.wait_for_finish(job_id, poll_interval=1, timeout=600)
    print("作业结果:", final_result)


if __name__ == "__main__":
    main()
