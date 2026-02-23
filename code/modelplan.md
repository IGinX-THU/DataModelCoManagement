 Proposed Plan


  模型模块增强实施计划

  ## 🎯 概要

  在不影响其他模块的前提下，根据《需求文档_1.26修改》3.2 与《概要文档2.7》3.2、4.2.2 的约束，完善模型资产管理子系统（后端+前端），确保“模型托管、元模型档案编辑/生成、版本管理、下载”全流程可演示。开发过程中逐
  文件记录职责，最终输出 modelmodule.md 汇总。

  ## ⚙️ 后端工作流

  1. 接口与数据结构梳理
      - 核对现有 /api/v1/models 系列接口与需求用例（MODEL-HOST-001/2/3；META-PROFILE-001/002）。
      - 明确输入输出 DTO（ModelUploadRequest 等）与 VO（ModelProfileVO、ModelVersionVO）字段需满足文档定义的属性（开发者、用途、IO Schema、版本列表、引用计数）。
  2. 模型托管完善
      - ModelAssetServiceImpl：
          - 扩展上传校验：文件类型白名单（py/mat/ame/dll/zip…）、大小上限、重复 MD5 检查、版本自动生成（若需求要求）。
          - IGinX FS 写入/删除异常捕获与事务回滚（借助 ModelFileStorageService）。
          - 版本历史：标记最新版本、计算引用次数（查询关联规则）。
      - ModelFileStorageService：
          - 确认路径组织（profileId/version/文件名），支持断点式流下载。
      - ModelSchemaParser：
          - 提供基础代码解析（解析 @Input/@Output 注解），并在接口 /parse 返回 ModelVersionVO 片段。
      - ModelAssetController：
          - 保持接口体裁与文档一致，下载接口支持大文件流、Content-Disposition 处理。
          - 删除模型/版本需检测 AssociationRuleRepository 引用，返回业务错误。
  3. 元模型档案编辑
      - ModelProfileUpdateRequest：包含名称、描述、开发者、用途、IO Schema。
      - ModelAssetServiceImpl.updateProfile：解析 IO Schema JSON、落库至 meta_model_profile + 同步最新版本的 schema（通过 JSONB）。
      - 添加差异比对（若文档要求）：可在 service 中提供对比逻辑返回至前端。
  4. 解析&自动生成
      - /api/v1/models/parse 接口接受临时文件，调用 ModelSchemaParser，输出 inputs/outputs/dependencies。
      - 上传时若用户未提供 schema，默认使用解析结果；若提供 ioSchema，需校验 JSON。
  5. 下载/导出
      - 下载接口读取 ModelAssetEntity.storagePath，校验文件存在；若缺失，抛出“文件索引存在但物理文件缺失”业务错误（与需求扩展场景一致）。
      - 可选：生成资源包导出（若需求 3.2.3 要求，可列为后续任务）。
  6. 日志与记录
      - 在 service 层统一记录操作（上传/删除/更新）便于审计（需求 6.4.2 可后续补充 hook）。

  ## 🖥️ 前端交互

  1. 数据层 (Pinia)
      - src/api/model.js：确保所有后端接口对应方法；新增获取详情、比对 diff 接口（若新增）。
      - src/stores/model.js：
          - 上传表单：增加开发者、用途、依赖信息、文件类型选择。
          - 状态管理：记录上传进度、解析结果、当前模型、版本选择。
          - 提供 compareVersions 数据，以支撑 Diff UI。
          - 错误处理：展示后端业务错误提示。
  2. 界面 (ModelAssetsView.vue + 组件)
      - 上传向导：按照需求流程（选择文件→解析→补充元数据→确认）显示步骤、进度条。
      - 模型列表：展示名称、类型、最新版本、引用次数、更新时间。
      - 详情面板：显示 inputs/outputs 表、元数据、版本历史、下载/删除按钮。
      - 元模型编辑器：可从最新版本自动填充，也可手动添加参数；支持保存后刷新列表。
      - Diff 弹窗：对比两个版本的 IO schema（字段变化突出）。
      - 关联提示：若模型被规则引用，删除按钮禁用并提示原因。
      - 文案/提示遵循需求中的提示语（上传成功、文件丢失等）。
  3. 体验补充
      - 错误行/解析失败提示（弹窗/Toast）。
      - 上传完成后自动选中最新模型，方便演示。
  ## 🧪 测试与验证
  1. 后端
      - 单元/集成：ModelAssetServiceImplTest
          - 上传合法文件，校验 profile/asset 入库、IO schema 保存、MD5/版本标记。
          - 上传重复版本→返回错误。
          - 删除引用中的模型→抛出 BizException。
          - 解析接口输入示例文件→校验解析结果。
          - 下载缺失文件→返回业务错误。
      - 若可行，可 Mock IGinX FS 写入/删除。
  2. 前端
      - 手动流程脚本（用于演示）：
          1. 上传 Python 模型（含 @Input/@Output）→自动解析→展示成功。
          2. 编辑元数据→保存→刷新列表。
          3. 上传新版本→版本历史更新→Diff 对比。
          4. 下载最新版本→浏览器下载。
          5. 删除版本/模型（未被引用）→成功；若被引用→提示。
      - 可选：编写 Cypress/Playwright 脚本（若时间允许）。

  ## 📝 文档 (modelmodule.md)

  - 目录建议：
      1. 模块概述与需求引用。
      2. 后端主要文件（Controller/Service/DTO/Entity/Util）职责与关键逻辑。
      3. 前端主要文件（API/Store/View/组件）职责。
      4. 数据流与交互流程（上传、解析、编辑、下载、删除）。
      5. 测试与演示方法（含命令/脚本）。
      6. 后续建议或遗留事项。
  - 每个文件条目需包含：路径、核心职责、关键方法/字段、与需求条目对应关系。
  - 记录新增/修改文件的作用与验证方式。

  ## 🚧 假设与约束

  - 目前数据库/实体已存在：model_asset、meta_model_profile 表结构满足需求文档描述。
  - IGinX 文件系统访问通过现有 ModelFileStorageService；无需新增底层集成。
  - RBAC 授权、审计日志暂不扩展；后续安全需求可另建任务。
  - 性能/断点续传等非功能需求可在功能稳定后迭代。

  ## 📌 交付物

  - 更新的后端 Java 代码（Controller/Service/DTO/Util）。
  - 前端 Vue/Pinia 改动。
  - 新增或更新的测试用例（若可）。
  - modelmodule.md 文档，详述本次修改。