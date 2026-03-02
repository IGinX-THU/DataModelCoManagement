#!/usr/bin/env python3
"""
根据 Mermaid 源生成 draw.io 可打开的 HTML 文件。

产物：
1. docs/design/diagrams/src/*.mmd
2. docs/design/diagrams/html/*-{type}-{timestamp}.html
3. docs/design/diagrams/html/latest/{id}-{type}.html
4. docs/design/diagrams/manifest.json
5. docs/design/diagrams/README.md
"""

from __future__ import annotations

import base64
import datetime as dt
import json
import webbrowser
import urllib.parse
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
DIAGRAM_ROOT = ROOT / "docs" / "design" / "diagrams"
SRC_DIR = DIAGRAM_ROOT / "src"
HTML_DIR = DIAGRAM_ROOT / "html"
LATEST_DIR = HTML_DIR / "latest"
MANIFEST_PATH = DIAGRAM_ROOT / "manifest.json"
README_PATH = DIAGRAM_ROOT / "README.md"


DIAGRAMS = [
    {
        "id": "overall-architecture",
        "title": "系统总体架构图",
        "type": "flow",
        "content": r"""
flowchart LR
    U[前端 Web 客户端] --> G[Spring Boot API 网关层]
    G --> C1[数据资源模块]
    G --> C2[模型资产模块]
    G --> C3[关联规则模块]
    G --> C4[任务调度模块]
    G --> C5[分析报告模块]
    G --> C6[系统管理模块]

    C1 --> IG[IGinX Session 封装层]
    C2 --> MFS[模型文件存储服务]
    C5 --> DFS[导出文件存储服务]
    C4 --> TS[线程池任务调度器]

    IG --> IGINX[(IGinX 服务)]
    C1 --> PG[(PostgreSQL 元数据库)]
    C2 --> PG
    C3 --> PG
    C4 --> PG
    C6 --> PG

    MFS --> IGINXFS[(IGinX 文件系统)]
    DFS --> LOCAL[(本地存储目录)]
""",
    },
    {
        "id": "data-source-onboarding",
        "title": "数据源接入流程图",
        "type": "flow",
        "content": r"""
flowchart TD
    A[提交数据源创建请求] --> B[参数校验]
    B --> C{数据源类型合法?}
    C -- 否 --> X1[返回 400]
    C -- 是 --> D[连通性测试]
    D --> E{测试通过?}
    E -- 否 --> X2[返回 400]
    E -- 是 --> F[校验名称与挂载路径唯一性]
    F --> G{时序数据源?}
    G -- 是 --> H[检查/注册 IGinX 存储引擎]
    G -- 否 --> I[跳过引擎注册]
    H --> J[加密连接配置并入库]
    I --> J
    J --> K[返回数据源 ID]
""",
    },
    {
        "id": "model-upload-version",
        "title": "模型上传与版本管理流程图",
        "type": "flow",
        "content": r"""
flowchart TD
    A[上传模型文件] --> B[文件合法性校验]
    B --> C[识别模型类型与版本号]
    C --> D[解析 IO Schema]
    D --> E[定位或创建模型档案]
    E --> F[存储模型文件到 IGinX FS]
    F --> G[旧版本 latest=false]
    G --> H[写入新版本 latest=true]
    H --> I[返回模型档案详情]

    J[删除模型版本] --> K{是否被规则引用?}
    K -- 是 --> X[拒绝删除]
    K -- 否 --> L[删除文件并删除版本记录]
    L --> M[重置最新版本标记]
""",
    },
    {
        "id": "rule-task-sequence",
        "title": "规则驱动任务执行时序图",
        "type": "seq",
        "content": r"""
sequenceDiagram
    participant Client as 前端
    participant TaskAPI as TaskController
    participant TaskSvc as TaskServiceImpl
    participant RuleRepo as AssociationRuleRepository
    participant ModelRepo as ModelAssetRepository
    participant Scheduler as TaskScheduler
    participant IG as IginxStorageWrapper
    participant DB as TaskRepository

    Client->>TaskAPI: POST /api/v1/tasks/submit
    TaskAPI->>TaskSvc: submitTask(request)
    TaskSvc->>RuleRepo: findById(ruleId)
    TaskSvc->>ModelRepo: findById(modelId)
    TaskSvc->>DB: save(PENDING task)
    TaskSvc->>Scheduler: submit(taskRunner)
    Scheduler-->>TaskSvc: accepted
    TaskSvc-->>Client: taskId

    Scheduler->>TaskSvc: executeTask(taskId)
    TaskSvc->>DB: update RUNNING + startTime
    TaskSvc->>IG: queryData(inputPaths, range)
    TaskSvc->>IG: insertColumnRecords(outputPaths)
    TaskSvc->>DB: update SUCCESS/FAILED + endTime
    TaskSvc->>Scheduler: clear(taskId)
""",
    },
    {
        "id": "analysis-export-report-sequence",
        "title": "分析导出与报告生成时序图",
        "type": "seq",
        "content": r"""
sequenceDiagram
    participant Client as 前端
    participant API as AnalysisController
    participant Svc as AnalysisServiceImpl
    participant TaskRepo as TaskRepository
    participant RuleRepo as AssociationRuleRepository
    participant IG as IginxStorageWrapper
    participant MFS as ModelFileStorageService
    participant DFS as DataFileStorageService

    Client->>API: POST /analysis/tasks/{id}/export
    API->>Svc: exportPackage(taskId, options)
    Svc->>TaskRepo: find task
    Svc->>RuleRepo: find rule + output paths
    Svc->>IG: query series(input/output)
    Svc->>MFS: read model bytes(optional)
    Svc->>DFS: create zip file
    Svc-->>API: /api/v1/data/files/{fileName}
    API-->>Client: download url

    Client->>API: POST /analysis/tasks/{id}/report
    API->>Svc: generateReport(taskId, options)
    Svc->>IG: query output series
    Svc->>Svc: calculate stats + build chart data
    Svc->>DFS: write pdf file
    Svc-->>API: /api/v1/data/files/{fileName}
""",
    },
    {
        "id": "task-state-flow",
        "title": "任务状态流转与调度流程图",
        "type": "flow",
        "content": r"""
flowchart LR
    P[PENDING] --> R[RUNNING]
    R --> S[SUCCESS]
    R --> F[FAILED]
    P --> A[ABORTED]
    R --> A

    Q1[线程池队列满] --> FQ[提交失败并标记 FAILED]
    STOP[用户停止任务] --> A
""",
    },
    {
        "id": "core-er-model",
        "title": "核心数据模型 ER 图",
        "type": "er",
        "content": r"""
erDiagram
    SYS_DATA_RESOURCE ||--o{ ASSOCIATION_RULE : "data_id"
    MODEL_ASSET ||--o{ ASSOCIATION_RULE : "model_id"
    META_MODEL_PROFILE ||--o{ MODEL_ASSET : "profile_id"
    ASSOCIATION_RULE ||--o{ TASK : "rule_id"
    SYS_DATA_RESOURCE ||--o{ DATA_EXPORT_TASK : "source_id"

    SYS_DATA_RESOURCE {
        bigint id PK
        string name
        string source_type
        string mount_path
        text conn_config
    }
    META_MODEL_PROFILE {
        bigint id PK
        string name
        jsonb io_schema
    }
    MODEL_ASSET {
        bigint id PK
        bigint profile_id FK
        string version
        string storage_path
        boolean is_latest
    }
    ASSOCIATION_RULE {
        bigint id PK
        bigint data_id FK
        bigint model_id FK
        string trigger_type
        jsonb mapping_json
        jsonb output_target
        boolean enabled
    }
    TASK {
        string id PK
        bigint rule_id FK
        string status
        datetime range_start
        datetime range_end
        string result_link
    }
    DATA_EXPORT_TASK {
        bigint id PK
        bigint source_id FK
        string export_type
        string format
        string status
        string file_name
    }
""",
    },
    {
        "id": "deployment-topology",
        "title": "部署拓扑与依赖关系图",
        "type": "flow",
        "content": r"""
flowchart TB
    subgraph ClientZone[客户端区]
        FE[Vue 前端]
    end

    subgraph AppZone[应用区]
        APP[iginx-assoc-backend\nSpring Boot :8080]
    end

    subgraph DataZone[数据区]
        PG[(PostgreSQL)]
        IGINX[(IGinX)]
        IFS[(IGinX FileSystem)]
        FS[(本地文件目录 storage/data)]
    end

    FE --> APP
    APP --> PG
    APP --> IGINX
    APP --> IFS
    APP --> FS
""",
    },
]


def _compress_payload(diagram_type: str, content: str) -> str:
    compressed = zlib.compress(content.encode("utf-8"), 9)
    b64 = base64.b64encode(compressed).decode("utf-8")
    payload = json.dumps(
        {"type": diagram_type, "compressed": True, "data": b64},
        ensure_ascii=False,
    )
    return urllib.parse.quote(payload, safe="")


def _build_url(diagram_type: str, content: str) -> str:
    encoded = _compress_payload(diagram_type, content)
    return f"https://app.diagrams.net/?p={encoded}"


def _write_html(path: Path, title: str, url: str) -> None:
    html = f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>{title}</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; margin: 32px; }}
    .card {{ max-width: 860px; border: 1px solid #ddd; border-radius: 12px; padding: 20px; }}
    .btn {{ display: inline-block; margin-top: 12px; background: #1677ff; color: #fff; text-decoration: none; padding: 10px 16px; border-radius: 8px; }}
    code {{ background: #f5f5f5; padding: 2px 6px; border-radius: 4px; }}
  </style>
</head>
<body>
  <div class="card">
    <h2>{title}</h2>
    <p>该页面由脚本自动生成，用于打开 draw.io 图编辑器。</p>
    <p><a class="btn" href="{url}" target="_blank" rel="noopener noreferrer">打开 draw.io 图</a></p>
    <p>说明：URL 为压缩编码内容，请勿手动编辑。</p>
  </div>
  <script>
    // 在本地桌面环境可直接自动打开；无图形界面环境会被忽略。
    try {{ window.open("{url}", "_blank"); }} catch (e) {{}}
  </script>
</body>
</html>
"""
    path.write_text(html, encoding="utf-8")


def main() -> None:
    SRC_DIR.mkdir(parents=True, exist_ok=True)
    HTML_DIR.mkdir(parents=True, exist_ok=True)
    LATEST_DIR.mkdir(parents=True, exist_ok=True)

    timestamp = dt.datetime.now().strftime("%Y%m%d%H%M%S")
    manifest = []

    for item in DIAGRAMS:
        diagram_id = item["id"]
        title = item["title"]
        diagram_type = item["type"]
        content = item["content"].strip() + "\n"

        src_name = f"{diagram_id}-{diagram_type}.mmd"
        ts_html_name = f"{diagram_id}-{diagram_type}-{timestamp}.html"
        latest_html_name = f"{diagram_id}-{diagram_type}.html"

        src_path = SRC_DIR / src_name
        ts_html_path = HTML_DIR / ts_html_name
        latest_html_path = LATEST_DIR / latest_html_name

        src_path.write_text(content, encoding="utf-8")
        url = _build_url(diagram_type, content)
        _write_html(ts_html_path, title, url)
        _write_html(latest_html_path, title, url)

        manifest.append(
            {
                "id": diagram_id,
                "title": title,
                "type": diagram_type,
                "source": str(src_path.relative_to(ROOT)).replace("\\", "/"),
                "html_timestamped": str(ts_html_path.relative_to(ROOT)).replace("\\", "/"),
                "html_latest": str(latest_html_path.relative_to(ROOT)).replace("\\", "/"),
            }
        )

        # 按技能要求尝试直接打开浏览器；无 GUI 环境可能无效，但不影响产物。
        try:
            webbrowser.open(url)
        except Exception:
            pass

    MANIFEST_PATH.write_text(
        json.dumps(
            {
                "generated_at": dt.datetime.now().isoformat(timespec="seconds"),
                "count": len(manifest),
                "diagrams": manifest,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    lines = [
        "# 详细设计图清单",
        "",
        "## 使用说明",
        "",
        "- `src/` 目录为 Mermaid 源文件，可继续维护。",
        "- `html/latest/` 目录为稳定引用入口，文档建议引用这里。",
        "- `html/*.html` 为时间戳快照。",
        "",
        "## 图列表",
        "",
    ]
    for i, item in enumerate(manifest, 1):
        lines.extend(
            [
                f"### 图{i}：{item['title']}",
                f"- 稳定入口：`{item['html_latest']}`",
                f"- 快照入口：`{item['html_timestamped']}`",
                f"- 源文件：`{item['source']}`",
                "",
            ]
        )
    README_PATH.write_text("\n".join(lines), encoding="utf-8")

    print(f"generated diagrams: {len(manifest)}")
    print(f"manifest: {MANIFEST_PATH}")
    print(f"readme: {README_PATH}")


if __name__ == "__main__":
    main()

