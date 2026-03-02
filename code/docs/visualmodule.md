# 可视化分析模块交付说明

## 模块定位
- **目标**：按照《需求文档_1.26修改》3.4 与《概要文档2.7》3.4/4.2.4 要求，提供“单任务曲线展示、任务对比、指标统计、任务监控、导出报告/资源包”等可视化能力。
- **范围**：后端新增 Analysis API，前端整合 AnalysisView、nalysis.js API 与 store；与任务模块联动，但不改动数据/模型/关联模块核心逻辑。

## 后端实现摘要
| 文件 | 作用 |
| --- | --- |
| nalysis/controller/AnalysisController.java | 暴露 /api/v1/analysis REST 接口：任务曲线、任务对比、导出资源包、生成实验报告。|
| nalysis/service/AnalysisService.java | 定义曲线查询、任务对比、导出与报告生成方法。|
| nalysis/service/impl/AnalysisServiceImpl.java | 当前实现返回模拟数据：为每个任务生成输入/输出曲线、随机数值、报告/包下载链接。后续可替换为实际 IGinX 查询与文件打包。|
| nalysis/dto/* | TaskSeriesRequest/TaskCompareRequest/TaskReportRequest/TaskExportRequest 定义请求体；TaskSeriesResponse 作为统一返回模型。|
| nalysis/vo/* | TaskSeriesVO, TaskSeriesPointVO 描述曲线结构，前端直接消费。|

## 前端实现摘要 *(需在后续迭代中完成)*
- src/api/analysis.js：封装 /analysis 系列 API。
- src/stores/analysis.js：管理曲线、指标、导出/报告状态，并与 ssociationStore 协同。
- src/views/AnalysisView.vue：
  - 左侧任务列表 + 多选。
  - Chart 面板（绝对/相对时间切换、ECharts 渲染）。
  - 指标卡片、导出/报告弹窗。
  - Tab 切换 Task Monitor / Result Analysis。
- TaskMonitorView.vue：复用已有视图展示任务状态。

## 功能演示（当前仍为占位）
1. 在“可视化分析”页选择任务 → 调用 /analysis/tasks/{taskId}/series 获取曲线。
2. 勾选多个任务 → 调用 /analysis/tasks/compare 获取对比曲线。
3. 点击“Export Resource Package” → 调 /analysis/tasks/{taskId}/export，返回 ZIP 链接。
4. 点击“Generate Report” → 调 /analysis/tasks/{taskId}/report，返回 PDF 链接。

## 约束与后续计划
- 当前后端仅输出模拟数据，尚未接入真实 IGinX 查询；需在后续迭代中实现真实数据访问与降采样逻辑（概要文档 8.3.2）。
- 前端仍需补充 API、Store 与页面联动；本说明先记录模块结构，后续完成后可更新内容。
- 导出/报告接口目前返回占位 URL，后续可接入真实 zip/pdf 生成与下载。
