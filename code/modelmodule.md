# 模型资产管理模块交付说明

## 1. 模块概览
- **目标**：实现《需求文档_1.26修改》3.2 与《概要文档2.7》3.2/4.2.2 中“模型托管、元模型档案、版本管理、下载/删除、接口解析”等功能，使模型从上传、管理到下载全过程可演示。
- **范围**：后端 iginx-assoc-backend 的 com.xmu.iginx.assoc.modules.model 包 + 前端 iginx-assoc-ui/src 中的模型资产页面、Store 与 API。
- **数据实体**：meta_model_profile（档案基本信息+IO Schema）与 model_asset（每个版本的物理文件/MD5/类型/IO Schema）。

## 2. 后端实现摘要
| 文件 | 作用 |
| --- | --- |
| controller/ModelAssetController.java | 暴露 /api/v1/models REST 接口：列表、详情、上传、更新档案、删除档案/版本、下载文件、解析接口。全部接口使用 Result<> 包装，满足概要文档 4.2.2 契约。 |
| dto/ModelUploadRequest.java | 上传请求体，含 profileId/name/description/developer/usageScope/version/type/ioSchema，映射需求文档中“填写基本信息”步骤。 |
| dto/ModelProfileUpdateRequest.java | 档案编辑请求，支持名称、描述、开发者、用途、IO Schema 修改。 |
| entity/MetaModelProfileEntity.java | 映射 meta_model_profile 表，记录模型档案及 io_schema(JSONB)。 |
| entity/ModelAssetEntity.java | 映射 model_asset 表，保存物理版本（文件名/类型/MD5/大小/路径/是否最新）。 |
| epository/*.java | ModelAssetRepository、MetaModelProfileRepository 提供版本/档案查询辅助。 |
| service/ModelAssetService.java | 定义服务接口，供 Controller 调用。 |
| service/impl/ModelAssetServiceImpl.java | 模块核心：
  - uploadModel：校验文件类型&大小、解析或接收 IO Schema、创建档案/版本、写入 IGinX FS（通过 ModelFileStorageService）、计算 MD5、更新 isLatest。
  - updateProfile：编辑档案并同步最新版本的 schema。
  - deleteProfile / deleteVersion：在删除前检查 AssociationRuleRepository 是否引用，删除时移除本地文件。
  - parseSchema：调用 ModelSchemaParser 对 @Input/@Output 注释进行提取。
  - 工具方法：alidateFileType、esolveProfile、	oProfileVO 等。
| util/ModelFileStorageService.java | 负责按 models/<type>/<profileId>/<version>/ 生成路径，写入与解析 iginx:// URI，并支持下载时定位本地文件。 |
| util/ModelSchemaParser.java | 基于正则解析模型源码中的 @Input/@Output 注释，生成 ModelIoSchema。 |
| o/ModelProfileVO.java、ModelVersionVO.java | 返回给前端的视图对象，包含模型元数据、历史版本、引用次数、IO Schema 列表，用于界面展示。

### 关键业务流程
1. **上传**：POST /api/v1/models/upload → ModelUploadRequest + 文件。服务端校验类型/大小、解析 schema、写入 IGinX FS、保存档案与版本，并返回最新 ModelProfileVO。
2. **解析接口**：POST /api/v1/models/parse 接受文件，返回 ModelVersionVO 中的 inputs/outputs，供前端自动填充。
3. **档案编辑**：PUT /api/v1/models/{id} 更新名称、描述、开发者、用途及 IO Schema。
4. **删除**：DELETE /api/v1/models/{id} 或 /assets/{assetId}，删除前检查是否被关联规则引用。
5. **下载**：GET /api/v1/models/assets/{assetId}/download，将存储路径映射到本地文件并流式响应；文件丢失时抛出业务异常。

## 3. 前端实现摘要
| 文件 | 作用 |
| --- | --- |
| src/api/model.js | 定义模型模块 REST 调用：列表、详情、上传、更新、删除、版本删除、解析、下载 URL。 |
| src/stores/model.js | Pinia Store：
  - models/selectedModel 状态与加载逻辑。
  - 上传表单：管理名称/版本/类型/文件/解析结果。
  - uploadModelAsset、updateModelMetadata、deleteModel、deleteModelVersion、downloadModel 等动作对应后端接口。
  - parseSchemaByFile 调用 /parse 接口自动提取 IO Schema。
| src/views/ModelAssetsView.vue | 复刻“模型上传向导 + 元模型编辑器 + 模型资产库”界面：
  - 上传步骤：选择文件→解析→补充元数据→提交，显示进度条提示。
  - 模型列表与详情面板：展示最新版本基本信息、inputs/outputs 表格、版本历史（可选择两个版本进行 diff）。
  - 元模型编辑器：可手动调整 IO Schema，保存后调用 updateModelMetadata。
  - 下载/删除按钮与模态框，联动 Store 状态。
| 其他组件（RibbonToolbar、Sidebar 等） | 已内置触发上传、编辑、删除、下载的入口，与 modelStore 状态联动。

## 4. 功能演示步骤
1. **上传模型**：在“模型资产”页点击“上传模型”→选择 .py/.dll/.zip 等文件→自动解析接口→填写开发者、用途、版本→提交。界面会显示进度并提示成功。
2. **查看与编辑档案**：点击列表项查看最新版本 inputs/outputs、元数据、引用次数；点击“元模型编辑器”调整接口描述，保存后即时刷新。
3. **上传新版本**：选择“关联已有模型”，填入新版本号并上传文件；历史版本栏将出现多条记录，可勾选两条进行差异对比。
4. **下载/删除**：在详情面板点击“下载”则调用 /download 接口；若未被关联规则引用，可在模态框确认后删除模型或单个版本。

## 5. 验证与待办
- **验证**：目前尚未新增自动化测试；建议后续为 ModelAssetServiceImpl 补充 JUnit（覆盖上传、删除、解析）以及前端编写 E2E 脚本。
- **遗留/改进建议**：
  - 审计日志与 RBAC（需求文档 6.4）留待安全迭代。
  - 下载接口的断点续传、病毒扫描、Zip 递归校验等非功能需求未实现，可排后续任务。
  - 若需多语言模型包装，可在 ModelFileStorageService 中扩展压缩策略与依赖描述。

---
通过本说明即可了解模型模块的主要文件、职能及演示路径。如需进一步细化测试脚本或接入其他子系统，请告知。
