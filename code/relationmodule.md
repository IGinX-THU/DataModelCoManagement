# 关联管理模块交付说明

## 模块目标与范围
- **模块定位**：实现《需求文档_1.26修改》3.3 与《概要文档2.7》3.3/4.2.3 中“关联配置 + 任务执行”闭环功能，连接数据模块与模型模块。
- **覆盖子系统**：后端 com.xmu.iginx.assoc.modules.relation 与 modules.task，前端 src/stores/association.js、src/views/AssociationView.vue 及 src/api/association.js。
- **实现概况**：支持关联规则新建/编辑/复制/禁用/删除、绑定数据路径与模型参数、手动触发任务、查看/终止任务；所有接口统一使用 Result<T> 响应。

## 后端实现摘要
| 文件 | 主要职责 |
| --- | --- |
| elation/controller/AssociationRuleController.java | 提供 /api/v1/rules REST 接口，包括创建、更新、启停、删除、详情与列表。 |
| elation/dto/*Request.java | 约束请求体字段：规则名称、模型版本 ID、触发类型、Cron、bindings/results 映射等，与文档中的向导步骤一一对应。 |
| elation/entity/AssociationRuleEntity.java | 映射 ssociation_rule 表，mapping_json 与 output_target 均存储为 JSONB，保持可扩展。 |
| elation/service/impl/AssociationRuleServiceImpl.java | 
  - 在创建/更新时读取模型的 IO Schema（ModelAssetRepository + MetaModelProfileRepository），校验必填输入与输出映射完整性。
  - 构建标准 JSON 结构：mappings 数组 + output_target.paths，便于 Task 模块消费。
  - 删除前查询 TaskRepository，防止正在运行的任务被破坏。
  - 	oVO 拼装模型名称、版本、类型、最近更新时间、映射详情。
| elation/vo/AssociationRuleVO.java | 前端展示对象，包含规则基本信息、绑定映射、状态、模型元数据。 |
| 	ask/controller/TaskController.java | 提供 /api/v1/tasks 获取任务、提交任务 /submit、终止任务 /{id}/stop。 |
| 	ask/service/impl/TaskServiceImpl.java | 
  - submitTask：校验规则启用、时间窗口合法、模型可用，写入 TaskEntity 并通过 TaskScheduler 异步执行。
  - stopTask：调用调度器取消任务并标记 ABORTED。
  - executeTask：示例化执行流程（可替换为真实调度），记录 execLog、esultLink、状态与起止时间。
| 	ask/repository/TaskRepository.java | 提供规则维度查询与运行中任务检测。

## 前端实现摘要
| 文件 | 主要职责 |
| --- | --- |
| src/api/association.js | 定义规则 CRUD、状态切换、任务提交/停止、任务列表接口，所有请求复用 equest wrapper。 |
| src/stores/association.js | Pinia Store：管理 ules、	asks、showWizard 等状态；提供 ddRule/updateRule/deleteRule/toggleRule/createTask/stopTask/loadRules/loadTasks 方法，封装 API 调用及状态刷新。 |
| src/views/AssociationView.vue | 关联页面：
  - 左侧规则列表 + 操作按钮（运行、编辑、复制、启停、删除）；
  - 右侧详情显示模型 / 绑定映射 / 任务历史；
  - 多步骤向导（选择模型 → 输入绑定 → 输出绑定 → 预览保存），支持数据路径选择器；
  - “运行”弹窗配置时间范围，提交后刷新任务；
  - 任务列表可停止运行任务、查看状态与日志。 |
| 其他 UI 组件 | RibbonToolbar、Sidebar 等提供入口与状态联动。

## 数据流与关键交互
1. **创建规则**：前端向导收集模型、输入/输出映射 → POST /api/v1/rules → 服务端校验 bindings/results 与 IO Schema → 持久化 JSON 映射 → 返回 AssociationRuleVO → 前端列表刷新。
2. **管理规则**：列表支持启用/禁用（PUT /status）、复制（复用创建接口）、删除（若有运行中任务则提示失败）。
3. **运行任务**：点击运行按钮 → 弹窗输入时间范围 → POST /api/v1/tasks/submit → 生成任务 ID 并入队 → 前端轮询/刷新任务列表。
4. **终止任务**：在任务列表选择 RUNNING 任务 → POST /api/v1/tasks/{id}/stop → 状态更新为 ABORTED，execLog 记录终止原因。

## 演示脚本
1. 在“模型资产”页上传并确认模型 IO Schema；切换至“关联管理”页。
2. 点击“创建关联规则” → 选择模型 → 绑定输入/输出路径 → 填写名称 → 保存；列表出现新规则，可立即复制或编辑。
3. 在新规则行点击“运行” → 设置时间范围 → 提交 → 任务列表看到新建任务进入 PENDING/RUNNING，完成后转为 SUCCESS。
4. 再次运行获得多个任务，可选择 RUNNING 任务点击“停止”触发 stopTask。
5. 尝试删除启用中的规则：若存在运行任务则提示“该规则有正在执行的任务，无法删除”；待任务完成或停止后再删除成功。

## 测试与后续建议
- **验证点**：
  - 创建规则时缺少必要绑定→后端返回 400。
  - 时间范围无效→任务提交失败。
  - 规则禁用后提交任务→接口报错提示未启用。
  - 删除规则时存在运行任务→被阻止。
  - 终止任务后状态更新、execLog 记录。
- **待办**（非功能需求，可后续迭代）：
  - Cron 自动调度执行、任务并发控制。
  - 规则/任务列表分页与搜索。
  - 更精细的类型兼容校验（含单位/维度）。
  - 任务执行真实调度逻辑（替换示例 Thread.sleep）。

通过本文档即可快速了解关联模块的代码结构、接口行为与演示路径，便于演示与后续扩展。
