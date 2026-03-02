# IGinX Assoc Backend API 文档（源码整理）

> 生成日期：2026-03-01  
> 源码范围：`src/main/java/com/xmu/iginx/assoc/modules/**/controller/*.java`  
> 接口总数：`52`（业务接口）  
> 说明：`/api/v1/data/sources` 与 `/api/v1/datasources` 为同义路由（数据源模块共 8 个接口同时支持两套前缀）。

---

## 1. 服务基础信息

- 默认 Base URL：`http://127.0.0.1:8080`
- 统一前缀：大部分业务接口使用 `/api/v1/**`
- 默认请求体：`application/json`
- 文件上传接口：`multipart/form-data`
- 文件下载接口：`application/octet-stream`
- 鉴权状态：当前配置为 `permitAll`（开发态全部放行）
- CORS 允许来源：
- `http://localhost:5173`
- `http://127.0.0.1:5173`

---

## 2. 统一响应与错误约定

### 2.1 统一响应结构 `Result<T>`

```json
{
  "code": 200,
  "msg": "Success",
  "data": {},
  "timestamp": 1700000000000
}
```

- `code`：业务状态码（非 HTTP 状态码）
- `msg`：提示消息
- `data`：业务数据（可为 `null`）
- `timestamp`：服务端毫秒时间戳

### 2.2 分页结构 `PageResult<T>`

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 10
}
```

### 2.3 常见错误码

- `400`：参数错误 / 业务校验失败
- `401`：未授权
- `403`：禁止访问
- `404`：资源不存在
- `500`：系统内部错误
- `503`：系统繁忙（任务调度压力过高）

---

## 3. 数据模型定义（请求/响应）

## 3.1 请求模型

### DataSourceConnectionConfig
- `host` string 必填
- `port` integer 必填，范围 `0~65535`
- `database` string 必填
- `username` string 必填
- `password` string 必填
- `extra` string 可选

### DataSourceCreateRequest
- `name` string 必填
- `sourceType` string 必填，可选值：`INFLUXDB` / `IOTDB` / `POSTGRESQL`
- `mountPath` string 必填（挂载路径）
- `description` string 可选
- `connectionConfig` DataSourceConnectionConfig 可选

### DataSourceUpdateRequest
- `name` string 必填
- `description` string 可选
- `connectionConfig` DataSourceConnectionConfig 可选

### DataSourceQueryRequest（Query 参数）
- `name` string 可选
- `sourceType` string 可选
- `pageNum` integer 可选，默认 `1`，最小 `1`
- `pageSize` integer 可选，默认 `10`，范围 `1~100`

### DataSourceController.TestConnectionRequest
- `sourceType` string 必填
- `connectionConfig` DataSourceConnectionConfig 可选

### TimeSeriesColumnMappingDTO
- `column` string 必填（文件列名）
- `target` string 必填（目标测点）
- `dataType` string 可选

### TimeSeriesImportRequest
- `sourceId` long 必填
- `storageGroup` string 必填
- `timestampColumn` string 必填
- `timestampFormat` string 可选
- `mappings` TimeSeriesColumnMappingDTO[] 可选

### StructuredImportRequest
- `sourceId` long 必填
- `schema` string 必填
- `table` string 必填
- `autoCreateTable` boolean 可选，默认 `false`
- `conflictStrategy` string 可选，默认 `update`
- `fileType` string 可选
- `sheetIndex` integer 可选，默认 `0`
- `primaryKeys` string[] 可选

### TimeRangeDTO
- `start` string 必填
- `end` string 必填

### StructuredQueryCondition
- `logic` string 可选，默认 `AND`
- `field` string 可选
- `op` string 可选，默认 `=`
- `value` string 可选

### DataExportRequest
- `type` string 必填（导出类型）
- `sourceId` long 必填
- `format` string 必填（导出格式）
- `layout` string 可选
- `paths` string[] 可选（时序路径）
- `timeRange` TimeRangeDTO 可选
- `schema` string 可选
- `table` string 可选
- `conditions` StructuredQueryCondition[] 可选
- `sql` string 可选
- `async` boolean 可选

### TimeSeriesQueryRequest
- `sourceId` long 必填
- `paths` string[] 必填
- `timeRange` TimeRangeDTO 可选
- `downsample` boolean 可选，默认 `false`
- `aggregator` string 可选，默认 `AVG`
- `precisionMs` long 可选

### StructuredQueryRequest
- `sourceId` long 必填
- `schema` string 必填
- `table` string 必填
- `conditions` StructuredQueryCondition[] 可选
- `orderBy` string 可选
- `orderDirection` string 可选，默认 `ASC`
- `pageNum` integer 可选，默认 `1`
- `pageSize` integer 可选，默认 `50`，范围 `1~500`

### TimeSeriesDeleteRequest
- `sourceId` long 必填
- `paths` string[] 必填
- `timeRange` TimeRangeDTO 可选
- `operation` string 可选，默认 `delete`

### StructuredRowCreateRequest
- `sourceId` long 必填
- `schema` string 必填
- `table` string 必填
- `data` Map<String,Object> 必填

### StructuredRowUpdateRequest
- `sourceId` long 必填
- `schema` string 必填
- `table` string 必填
- `data` Map<String,Object> 必填（需含用于定位行的主键/内部键）

### StructuredRowDeleteRequest
- `sourceId` long 必填
- `schema` string 必填
- `table` string 必填
- `keys` Map<String,Object> 必填

### StorageGroupRequest
- `sourceId` long 必填
- `path` string 必填

### MeasurementRequest
- `sourceId` long 必填
- `path` string 必填
- `dataType` string 必填

### TableColumnDefinitionDTO
- `name` string 必填
- `type` string 必填
- `nullable` boolean 可选，默认 `true`

### TableCreateRequest
- `sourceId` long 必填
- `schema` string 必填
- `table` string 必填
- `columns` TableColumnDefinitionDTO[] 可选（业务上建议必填）
- `primaryKeys` string[] 可选

### TableDropRequest
- `sourceId` long 必填
- `schema` string 必填
- `table` string 必填

### ModelUploadRequest（multipart 表单字段）
- `profileId` long 可选（已有档案时可传）
- `name` string 可选（新建档案时建议传）
- `description` string 可选
- `developer` string 可选
- `usageScope` string 可选
- `version` string 必填
- `type` string 必填
- `ioSchema` string 可选（JSON 字符串）

### ModelProfileUpdateRequest
- `name` string 必填
- `description` string 可选
- `developer` string 可选
- `usageScope` string 可选
- `ioSchema` string 可选（JSON 字符串）

### AssociationRuleCreateRequest
- `name` string 必填
- `modelId` long 必填（模型版本 ID）
- `dataId` long 可选（数据源 ID）
- `triggerType` string 可选，默认 `MANUAL`
- `cronExp` string 可选
- `bindings` Map<String,String> 必填（模型输入绑定）
- `results` Map<String,String> 必填（模型输出绑定）
- `enabled` boolean 可选，默认 `true`

### AssociationRuleUpdateRequest
- `name` string 必填
- `triggerType` string 可选
- `cronExp` string 可选
- `bindings` Map<String,String> 必填
- `results` Map<String,String> 必填

### RuleStatusRequest
- `enabled` boolean 必填

### TaskSubmitRequest
- `ruleId` long 必填
- `timeRange` object 必填
- `timeRange.start` LocalDateTime 必填，格式 `yyyy-MM-dd HH:mm:ss`
- `timeRange.end` LocalDateTime 必填，格式 `yyyy-MM-dd HH:mm:ss`

### TaskSeriesRequest（Query 参数）
- `relative` boolean 可选，默认 `false`

### TaskCompareRequest
- `taskIds` string[] 必填
- `mode` string 可选，默认 `absolute`

### TaskExportRequest
- `includeModel` boolean 可选，默认 `true`
- `includeInput` boolean 可选，默认 `true`
- `includeOutput` boolean 可选，默认 `true`
- `format` string 可选，默认 `CSV`

### TaskReportRequest
- `includeStats` boolean 可选，默认 `true`
- `includeCharts` boolean 可选，默认 `true`

### SqlExecuteRequest
- `sql` string 可选（业务上应传）
- `limit` integer 可选
- `formatTime` boolean 可选

## 3.2 响应模型

### DataSourceVO
- `id` long
- `name` string
- `sourceType` string
- `mountPath` string
- `description` string
- `createTime` LocalDateTime
- `connectionConfig` DataSourceConnectionConfigVO

### DataSourceConnectionConfigVO
- `host` string
- `port` integer
- `database` string
- `username` string
- `passwordMasked` string
- `extra` string

### DataSourceStructureNodeVO
- `id` string
- `name` string
- `type` string
- `children` DataSourceStructureNodeVO[]

### TableColumnVO
- `name` string
- `type` string
- `primaryKey` boolean
- `nullable` boolean

### DataImportResultVO
- `total` long
- `success` long
- `failed` long
- `errorFile` string
- `errorFileUrl` string

### DataExportResultVO
- `taskId` long
- `status` string（`PENDING`/`RUNNING`/`SUCCESS`/`FAILED`）
- `fileName` string
- `downloadUrl` string

### TimeSeriesQueryResultVO
- `timestamps` long[]
- `series` TimeSeriesSeriesVO[]

### TimeSeriesSeriesVO
- `path` string
- `values` object[]

### StructuredQueryResultVO
- `columns` string[]
- `page` PageResult<Map<String,Object>>

### ModelProfileVO
- `id` long
- `name` string
- `description` string
- `developer` string
- `usageScope` string
- `type` string
- `version` string
- `fileSize` long
- `uploadTime` LocalDateTime
- `refCount` long
- `updateTime` LocalDateTime
- `history` ModelVersionVO[]

### ModelVersionVO
- `id` long
- `version` string
- `fileType` string
- `fileSize` long
- `fileMd5` string
- `uploadTime` LocalDateTime
- `latest` boolean
- `inputs` ModelSchemaParam[]
- `outputs` ModelSchemaParam[]

### AssociationRuleVO
- `id` long
- `name` string
- `modelId` long
- `modelName` string
- `modelVersion` string
- `modelType` string
- `dataId` long
- `triggerType` string
- `cronExp` string
- `bindings` Map<String,String>
- `results` Map<String,String>
- `enabled` boolean
- `updateTime` LocalDateTime

### TaskVO
- `id` string
- `ruleId` long
- `status` string（`PENDING`/`RUNNING`/`SUCCESS`/`FAILED`/`ABORTED`）
- `rangeStart` LocalDateTime
- `rangeEnd` LocalDateTime
- `startTime` LocalDateTime
- `endTime` LocalDateTime
- `resultLink` string
- `execLog` string
- `createTime` LocalDateTime

### TaskSeriesVO
- `taskId` string
- `label` string
- `type` string
- `unit` string
- `relative` boolean
- `points` TaskSeriesPointVO[]

### TaskSeriesPointVO
- `timestamp` long
- `value` double

### DashboardSummaryVO
- `modelCount` long
- `ruleCount` long
- `dataSourceCount` long
- `taskCount` long
- `runningTaskCount` long
- `successTaskCount` long
- `failedTaskCount` long
- `taskTrend` DashboardTrendPointVO[]
- `recentTasks` DashboardRecentTaskVO[]

### DashboardTrendPointVO
- `date` string
- `taskCount` long
- `avgDurationSec` double

### DashboardRecentTaskVO
- `id` string
- `ruleName` string
- `modelName` string
- `modelType` string
- `status` string
- `startTime` LocalDateTime
- `endTime` LocalDateTime
- `createTime` LocalDateTime
- `durationSec` long

### SqlExecuteResultVO
- `sqlType` string
- `message` string
- `executionTimeMs` long
- `columns` string[]
- `rows` Map<String,Object>[]

### SystemLogEntryVO
- `id` string
- `time` string
- `level` string
- `component` string
- `message` string

---

## 4. 接口明细（全量）

## 4.1 首页与系统

### API-01 获取首页提示
- 方法：`GET`
- 路径：`/`
- 请求：无
- 响应：`Result<String>`

### API-02 系统健康检查
- 方法：`GET`
- 路径：`/api/v1/sys/health`
- 请求：无
- 响应：`Result<String>`

### API-03 系统日志查询
- 方法：`GET`
- 路径：`/api/v1/sys/logs`
- Query：`limit?`、`level?`、`keyword?`
- 响应：`Result<List<SystemLogEntryVO>>`

### API-04 SQL 控制台执行
- 方法：`POST`
- 路径：`/api/v1/sys/sql`
- Body：`SqlExecuteRequest`
- 响应：`Result<SqlExecuteResultVO>`

### API-05 仪表盘汇总
- 方法：`GET`
- 路径：`/api/v1/dashboard/summary`
- 请求：无
- 响应：`Result<DashboardSummaryVO>`

## 4.2 数据源管理（DataSourceController）

> 下列接口同时支持前缀：`/api/v1/data/sources` 与 `/api/v1/datasources`。

### API-06 新增数据源
- 方法：`POST`
- 路径：`/api/v1/data/sources`
- Body：`DataSourceCreateRequest`
- 响应：`Result<Long>`（数据源 ID）

### API-07 分页查询数据源
- 方法：`GET`
- 路径：`/api/v1/data/sources`
- Query：`DataSourceQueryRequest`
- 响应：`Result<PageResult<DataSourceVO>>`

### API-08 数据源详情
- 方法：`GET`
- 路径：`/api/v1/data/sources/{id}`
- Path：`id`（long）
- 响应：`Result<DataSourceVO>`

### API-09 数据源结构预览
- 方法：`GET`
- 路径：`/api/v1/data/sources/{id}/structure`
- Path：`id`（long）
- 响应：`Result<List<DataSourceStructureNodeVO>>`

### API-10 查询关系表字段
- 方法：`GET`
- 路径：`/api/v1/data/sources/{id}/tables/{schema}/{table}/columns`
- Path：`id`（long）、`schema`（string）、`table`（string）
- 响应：`Result<List<TableColumnVO>>`

### API-11 更新数据源
- 方法：`PUT`
- 路径：`/api/v1/data/sources/{id}`
- Path：`id`（long）
- Body：`DataSourceUpdateRequest`
- 响应：`Result<Void>`

### API-12 删除数据源
- 方法：`DELETE`
- 路径：`/api/v1/data/sources/{id}`
- Path：`id`（long）
- Query：`force?`（boolean，默认 `false`）
- 响应：`Result<Void>`

### API-13 测试数据源连接
- 方法：`POST`
- 路径：`/api/v1/data/sources/test-connection`
- Body：`TestConnectionRequest`
- 响应：`Result<Void>`

## 4.3 数据导入/导出/查询/维护（DataResourceController）

### API-14 时序数据导入
- 方法：`POST`
- 路径：`/api/v1/data/import/ts`
- Content-Type：`multipart/form-data`
- 表单字段：`request`=`TimeSeriesImportRequest`，`file`=`MultipartFile`
- 响应：`Result<DataImportResultVO>`

### API-15 结构化数据导入
- 方法：`POST`
- 路径：`/api/v1/data/import/struct`
- Content-Type：`multipart/form-data`
- 表单字段：`request`=`StructuredImportRequest`，`file`=`MultipartFile`
- 响应：`Result<DataImportResultVO>`

### API-16 发起数据导出
- 方法：`POST`
- 路径：`/api/v1/data/export`
- Body：`DataExportRequest`
- 响应：`Result<DataExportResultVO>`

### API-17 查询导出任务
- 方法：`GET`
- 路径：`/api/v1/data/export/tasks/{taskId}`
- Path：`taskId`（long）
- 响应：`Result<DataExportResultVO>`

### API-18 时序查询
- 方法：`POST`
- 路径：`/api/v1/data/query/ts`
- Body：`TimeSeriesQueryRequest`
- 响应：`Result<TimeSeriesQueryResultVO>`

### API-19 结构化查询
- 方法：`POST`
- 路径：`/api/v1/data/query/struct`
- Body：`StructuredQueryRequest`
- 响应：`Result<StructuredQueryResultVO>`

### API-20 时序删除/维护
- 方法：`POST`
- 路径：`/api/v1/data/ts/delete`
- Body：`TimeSeriesDeleteRequest`
- 响应：`Result<Void>`

### API-21 新增结构化行
- 方法：`POST`
- 路径：`/api/v1/data/struct/rows`
- Body：`StructuredRowCreateRequest`
- 响应：`Result<Void>`

### API-22 更新结构化行
- 方法：`PUT`
- 路径：`/api/v1/data/struct/rows`
- Body：`StructuredRowUpdateRequest`
- 响应：`Result<Void>`

### API-23 删除结构化行
- 方法：`DELETE`
- 路径：`/api/v1/data/struct/rows`
- Body：`StructuredRowDeleteRequest`
- 响应：`Result<Void>`

### API-24 创建存储组
- 方法：`POST`
- 路径：`/api/v1/data/structures/storage-groups`
- Body：`StorageGroupRequest`
- 响应：`Result<Void>`

### API-25 删除存储组
- 方法：`POST`
- 路径：`/api/v1/data/structures/storage-groups/drop`
- Body：`StorageGroupRequest`
- 响应：`Result<Void>`

### API-26 创建测点
- 方法：`POST`
- 路径：`/api/v1/data/structures/measurements`
- Body：`MeasurementRequest`
- 响应：`Result<Void>`

### API-27 删除测点
- 方法：`POST`
- 路径：`/api/v1/data/structures/measurements/drop`
- Body：`MeasurementRequest`
- 响应：`Result<Void>`

### API-28 创建表
- 方法：`POST`
- 路径：`/api/v1/data/structures/tables`
- Body：`TableCreateRequest`
- 响应：`Result<Void>`

### API-29 删除表
- 方法：`POST`
- 路径：`/api/v1/data/structures/tables/drop`
- Body：`TableDropRequest`
- 响应：`Result<Void>`

### API-30 下载导出文件
- 方法：`GET`
- 路径：`/api/v1/data/files/{fileName}`
- Path：`fileName`（string）
- 响应：二进制文件流（非 `Result` 包装）

## 4.4 模型资产管理（ModelAssetController）

### API-31 模型列表
- 方法：`GET`
- 路径：`/api/v1/models`
- 请求：无
- 响应：`Result<List<ModelProfileVO>>`

### API-32 模型详情
- 方法：`GET`
- 路径：`/api/v1/models/{id}`
- Path：`id`（long）
- 响应：`Result<ModelProfileVO>`

### API-33 上传模型文件
- 方法：`POST`
- 路径：`/api/v1/models/upload`
- Content-Type：`multipart/form-data`
- 表单字段：`ModelUploadRequest` 字段 + `file`
- 响应：`Result<ModelProfileVO>`

### API-34 更新模型档案
- 方法：`PUT`
- 路径：`/api/v1/models/{id}`
- Path：`id`（long）
- Body：`ModelProfileUpdateRequest`
- 响应：`Result<Void>`

### API-35 删除模型档案
- 方法：`DELETE`
- 路径：`/api/v1/models/{id}`
- Path：`id`（long）
- 响应：`Result<Void>`

### API-36 删除模型版本
- 方法：`DELETE`
- 路径：`/api/v1/models/assets/{assetId}`
- Path：`assetId`（long）
- 响应：`Result<Void>`

### API-37 下载模型文件
- 方法：`GET`
- 路径：`/api/v1/models/assets/{assetId}/download`
- Path：`assetId`（long）
- 响应：二进制文件流（`ResponseEntity<StreamingResponseBody>`）

### API-38 解析模型接口定义
- 方法：`POST`
- 路径：`/api/v1/models/parse`
- Content-Type：`multipart/form-data`
- 表单字段：`file`
- 响应：`Result<ModelVersionVO>`

## 4.5 关联规则管理（AssociationRuleController）

### API-39 创建关联规则
- 方法：`POST`
- 路径：`/api/v1/rules`
- Body：`AssociationRuleCreateRequest`
- 响应：`Result<Long>`（规则 ID）

### API-40 更新关联规则
- 方法：`PUT`
- 路径：`/api/v1/rules/{id}`
- Path：`id`（long）
- Body：`AssociationRuleUpdateRequest`
- 响应：`Result<Void>`

### API-41 更新规则启停状态
- 方法：`PUT`
- 路径：`/api/v1/rules/{id}/status`
- Path：`id`（long）
- Body：`RuleStatusRequest`
- 响应：`Result<Void>`

### API-42 删除关联规则
- 方法：`DELETE`
- 路径：`/api/v1/rules/{id}`
- Path：`id`（long）
- 响应：`Result<Void>`

### API-43 规则列表
- 方法：`GET`
- 路径：`/api/v1/rules`
- 请求：无
- 响应：`Result<List<AssociationRuleVO>>`

### API-44 规则详情
- 方法：`GET`
- 路径：`/api/v1/rules/{id}`
- Path：`id`（long）
- 响应：`Result<AssociationRuleVO>`

## 4.6 任务管理（TaskController）

### API-45 提交任务
- 方法：`POST`
- 路径：`/api/v1/tasks/submit`
- Body：`TaskSubmitRequest`
- 响应：`Result<String>`（任务 ID）

### API-46 终止任务
- 方法：`POST`
- 路径：`/api/v1/tasks/{id}/stop`
- Path：`id`（string）
- 响应：`Result<Void>`

### API-47 任务列表
- 方法：`GET`
- 路径：`/api/v1/tasks`
- Query：`ruleId?`（long）
- 响应：`Result<List<TaskVO>>`

### API-48 任务详情
- 方法：`GET`
- 路径：`/api/v1/tasks/{id}`
- Path：`id`（string）
- 响应：`Result<TaskVO>`

## 4.7 分析与报告（AnalysisController）

### API-49 获取任务曲线
- 方法：`GET`
- 路径：`/api/v1/analysis/tasks/{taskId}/series`
- Path：`taskId`（string）
- Query：`relative?`（boolean，默认 `false`）
- 响应：`Result<List<TaskSeriesVO>>`

### API-50 多任务曲线对比
- 方法：`POST`
- 路径：`/api/v1/analysis/tasks/compare`
- Body：`TaskCompareRequest`
- 响应：`Result<List<TaskSeriesVO>>`

### API-51 导出任务资源包
- 方法：`POST`
- 路径：`/api/v1/analysis/tasks/{taskId}/export`
- Path：`taskId`（string）
- Body：`TaskExportRequest`
- 响应：`Result<String>`（下载路径，如 `/api/v1/data/files/{fileName}`）

### API-52 生成实验报告
- 方法：`POST`
- 路径：`/api/v1/analysis/tasks/{taskId}/report`
- Path：`taskId`（string）
- Body：`TaskReportRequest`
- 响应：`Result<String>`（下载路径，如 `/api/v1/data/files/{fileName}`）

---

## 5. 备注

- 本文档按当前源码静态整理，不包含 `.bak` 备份文件中的控制器。
- 文件下载类接口（API-30、API-37）返回二进制流，不走统一 `Result<T>` 包装。
- 若后续启用鉴权，需要补充 `Authorization` 请求头规范与错误码策略。
