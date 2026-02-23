# 数据资源管理模块实现梳理

## 1. 模块定位与范围
- 负责“数据源接入、结构治理、数据导入/导出、查询与维护”的完整链路。
- 支持时序数据（InfluxDB/IoTDB，经 IGinX 统一访问）与结构化数据（PostgreSQL）。
- 文件导入/导出与错误日志统一落盘到 `assoc.storage.data-root` 对应目录。

## 2. 功能链路与实现文件（按功能）

### 2.1 数据源管理（新增/编辑/卸载/测试连接/结构预览）
- 前端入口
  - `iginx-assoc-ui/src/components/DataModals.vue`：新增数据源弹窗、卸载弹窗、连接测试。
  - `iginx-assoc-ui/src/stores/data.js`：`addSource/removeSource/testConnection/loadDataSources/loadDataSourceStructure`。
  - `iginx-assoc-ui/src/api/dataSource.js`：调用 `/api/v1/data/sources` 与 `/test-connection`。
- 后端入口
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/controller/DataSourceController.java`：新增、分页、详情、更新、删除、结构预览、连接测试。
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataSourceServiceImpl.java`：校验、连接测试、加密存储、IGinX 注册/卸载存储引擎、结构树构建。
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/DataSourceConnectionTestService.java`：Socket 级别连通性检测（3s 超时）。
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/IginxStorageEngineHelper.java`：生成 ADD/REMOVE STORAGEENGINE SQL（含 host 覆盖与参数拼接）。

### 2.2 结构治理（存储组/测点/表/字段）
- 前端入口
  - `iginx-assoc-ui/src/components/DataModals.vue`：创建存储组/测点/表的弹窗入口与按钮。
  - `iginx-assoc-ui/src/stores/data.js`：`createStorageGroup/dropStorageGroup/createMeasurement/dropMeasurement/createTable/dropTable`。
  - `iginx-assoc-ui/src/api/dataResource.js`：`/api/v1/data/structures/*`。
- 后端入口
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/controller/DataResourceController.java`：结构治理接口。
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/StructureServiceImpl.java`：
    - 时序：创建存储组（写入 __init__ 测点）、创建/删除测点。
    - 结构化：创建/删除表、读取表字段与主键。

### 2.3 时序数据导入（CSV/Excel）
- 前端入口
  - `iginx-assoc-ui/src/components/DataModals.vue`：多步骤导入向导、列映射与时间列配置。
  - `iginx-assoc-ui/src/stores/data.js`：`importTimeSeriesData`。
  - `iginx-assoc-ui/src/api/dataResource.js`：`/api/v1/data/import/ts`。
- 后端入口
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataImportServiceImpl.java`：
    - 解析 CSV/Excel，按列写入 IGinX（`insertColumnRecords`）。
    - 自动补齐测点路径（`storageGroup + '.' + column`）。
    - 时间解析走 `TimeParser`，失败行统计。

### 2.4 结构化数据导入（CSV/Excel/SQL）
- 前端入口
  - 同上导入向导，但导入类型为 `struct`。
- 后端入口
  - `DataImportServiceImpl`：
    - CSV/Excel：按表结构批量插入，支持自动建表与主键冲突策略。
    - SQL：逐条执行 SQL 语句。
    - 错误行会生成 CSV（`DataFileStorageService`）。

### 2.5 数据查询（时序/结构化）
- 前端入口
  - `iginx-assoc-ui/src/views/DataEditorView.vue`：时序时间范围查询 + 结构化过滤条件构建。
  - `iginx-assoc-ui/src/stores/data.js`：`queryTimeSeriesData/queryStructuredData`。
  - `iginx-assoc-ui/src/api/dataResource.js`：`/api/v1/data/query/ts`、`/api/v1/data/query/struct`。
- 后端入口
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataQueryServiceImpl.java`：
    - 时序：支持 downsample 与聚合类型。
    - 结构化：支持条件、分页、排序（`StructuredSqlBuilder`）。

### 2.6 数据维护（时序删除/结构化增删改）
- 前端入口
  - `iginx-assoc-ui/src/components/DataModals.vue`：时序维护弹窗。
  - `iginx-assoc-ui/src/views/DataEditorView.vue`：结构化表格行的新增/编辑/删除。
- 后端入口
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataMaintainServiceImpl.java`：
    - 时序：时间区间删除。
    - 结构化：按主键更新、按条件删除。

### 2.7 数据导出与下载
- 前端入口
  - `iginx-assoc-ui/src/components/DataModals.vue`：导出参数（格式、布局、SQL）。
  - `iginx-assoc-ui/src/stores/data.js`：`exportDataFile/pollExportTask`。
  - `iginx-assoc-ui/src/api/dataResource.js`：`/api/v1/data/export` 与 `/export/tasks/{id}`。
- 后端入口
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataExportServiceImpl.java`：
    - 时序导出 CSV/JSON（宽表/长表）。
    - 结构化导出 CSV/Excel/JSON。
    - 支持自动异步任务（`data_export_task` 表）。
  - `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/controller/DataResourceController.java`：文件下载 `/api/v1/data/files/{file}`。

## 3. 后端文件清单（逐文件说明）

### 3.1 Controller
- `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/controller/DataSourceController.java`：数据源 CRUD、分页、结构预览、连接测试。
- `iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/controller/DataResourceController.java`：导入、导出、查询、维护、结构治理与文件下载。

### 3.2 Service（接口）
- `.../service/DataSourceService.java`：数据源生命周期抽象。
- `.../service/DataImportService.java`：导入抽象。
- `.../service/DataExportService.java`：导出/任务抽象。
- `.../service/DataQueryService.java`：查询抽象。
- `.../service/DataMaintainService.java`：维护抽象。
- `.../service/StructureService.java`：结构治理抽象。
- `.../service/DataSourceConnectionTestService.java`：连接测试服务。
- `.../service/DataSourceAccessor.java`：实体与连接配置读取入口。

### 3.3 Service（实现）
- `.../service/impl/DataSourceServiceImpl.java`：
  - 校验、连接测试、IGinX 存储引擎注册/卸载。
  - 结构树构建（时序：`showColumns`；结构化：JDBC 元数据）。
- `.../service/impl/DataImportServiceImpl.java`：
  - 时序/结构化导入逻辑、批处理、错误行输出。
- `.../service/impl/DataExportServiceImpl.java`：
  - 同步/异步导出、导出任务落库、文件生成。
- `.../service/impl/DataQueryServiceImpl.java`：
  - 时序查询与降采样，结构化分页查询。
- `.../service/impl/DataMaintainServiceImpl.java`：
  - 时序区间删除，结构化行级增删改。
- `.../service/impl/StructureServiceImpl.java`：
  - 存储组/测点/表的创建与删除。

### 3.4 Repository
- `.../repository/DataResourceRepository.java`：数据源表 `sys_data_resource`。
- `.../repository/DataExportTaskRepository.java`：导出任务表 `data_export_task`。

### 3.5 Entity / Model
- `.../entity/DataResourceEntity.java`：数据源持久化对象。
- `.../entity/DataExportTaskEntity.java`：导出任务持久化对象。
- `.../model/DataSourceDetail.java`：数据源实体 + 类型 + 解密配置的聚合视图。

### 3.6 DTO（请求入参）
- `.../dto/DataSourceConnectionConfig.java`：host/port/database/username/password/extra。
- `.../dto/DataSourceCreateRequest.java`：name/sourceType/mountPath/connectionConfig/description。
- `.../dto/DataSourceUpdateRequest.java`：name/connectionConfig/description。
- `.../dto/DataSourceQueryRequest.java`：name/sourceType/pageNum/pageSize。
- `.../dto/TimeSeriesImportRequest.java`：sourceId/storageGroup/timestampColumn/format/mappings。
- `.../dto/TimeSeriesQueryRequest.java`：sourceId/paths/timeRange/downsample/aggregator/precision。
- `.../dto/TimeSeriesDeleteRequest.java`：sourceId/paths/timeRange/operation。
- `.../dto/TimeSeriesColumnMappingDTO.java`：CSV 列与测点映射、类型。
- `.../dto/StructuredImportRequest.java`：schema/table/主键/冲突策略/是否自动建表。
- `.../dto/StructuredQueryRequest.java`：schema/table/conditions/分页/排序。
- `.../dto/StructuredQueryCondition.java`：字段/运算符/逻辑/值。
- `.../dto/StructuredRowCreateRequest.java`：插入数据 map。
- `.../dto/StructuredRowUpdateRequest.java`：更新数据 map。
- `.../dto/StructuredRowDeleteRequest.java`：删除条件 key map。
- `.../dto/DataExportRequest.java`：导出类型/格式/路径/时间范围/SQL。
- `.../dto/StorageGroupRequest.java`：存储组路径。
- `.../dto/MeasurementRequest.java`：测点路径/类型。
- `.../dto/TableCreateRequest.java`：表名/字段/主键。
- `.../dto/TableDropRequest.java`：表名。
- `.../dto/TableColumnDefinitionDTO.java`：字段名/类型/是否可空。
- `.../dto/TimeRangeDTO.java`：start/end。

### 3.7 VO（响应视图）
- `.../vo/DataSourceVO.java`：数据源详情（含脱敏连接信息）。
- `.../vo/DataSourceConnectionConfigVO.java`：脱敏后的连接信息。
- `.../vo/DataSourceStructureNodeVO.java`：树节点（group/point/schema/table）。
- `.../vo/DataImportResultVO.java`：导入统计与错误文件链接。
- `.../vo/DataExportResultVO.java`：导出结果或任务状态。
- `.../vo/TimeSeriesQueryResultVO.java`：时间戳 + 多序列。
- `.../vo/TimeSeriesSeriesVO.java`：单条序列。
- `.../vo/StructuredQueryResultVO.java`：结构化分页结果。
- `.../vo/TableColumnVO.java`：表字段信息（含主键/可空）。

### 3.8 Util
- `.../util/ConnectionConfigCipher.java`：AES 加解密连接配置、密码脱敏。
- `.../util/CsvUtils.java`：CSV 解析与转义。
- `.../util/DataFileStorageService.java`：导出/错误文件落盘路径管理。
- `.../util/ExcelRowListener.java`：Excel 行读取监听。
- `.../util/IginxDataTypeConverter.java`：时序类型转换与值解析。
- `.../util/IginxStorageEngineHelper.java`：IGinX 存储引擎 SQL 拼接与 host 解析。
- `.../util/JdbcMetadataUtils.java`：读取表字段类型。
- `.../util/JdbcValueConverter.java`：结构化数据类型转换。
- `.../util/RelationalConnectionFactory.java`：PostgreSQL 连接工厂。
- `.../util/StructuredSqlBuilder.java`：结构化查询条件拼接。
- `.../util/TimeParser.java`：时间字符串/数值解析、毫秒/纳秒互转。

### 3.9 资源与配置
- `iginx-assoc-backend/src/main/resources/sql/schema.sql`：包含 `sys_data_resource` 与 `data_export_task`。
- `iginx-assoc-backend/src/main/resources/application.yml`：
  - `iginx.*`：IGinX 连接参数。
  - `assoc.storage.data-root`：导出/错误文件根目录。
  - `spring.servlet.multipart.*`：导入文件大小限制。

## 4. 前端文件清单（逐文件说明）

### 4.1 路由与页面
- `iginx-assoc-ui/src/router/index.js`：`/data` 路由指向 `DataEditorView`。
- `iginx-assoc-ui/src/views/DataEditorView.vue`：
  - 时序查询、折线图展示（ECharts）。
  - 结构化数据表格与行级编辑。
  - 结构树/拓扑预览。

### 4.2 组件与状态
- `iginx-assoc-ui/src/components/DataModals.vue`：
  - 新增/卸载数据源、导入向导、导出设置、维护操作。
- `iginx-assoc-ui/src/stores/data.js`：
  - 维护数据源树、当前选中节点、导入/导出/查询/维护逻辑。

### 4.3 API 封装
- `iginx-assoc-ui/src/api/dataSource.js`：数据源 CRUD、结构预览、连接测试。
- `iginx-assoc-ui/src/api/dataResource.js`：导入、导出、查询、维护、结构治理接口。

## 5. 关键依赖与边界
- IGinX SDK 统一访问时序数据；PostgreSQL 直接 JDBC 访问结构化数据。
- 存储组/测点创建为“写入 __init__ 测点”方式，属于轻量化结构管理。
- 导入/导出文件由后端落盘生成，前端通过 `/api/v1/data/files/{file}` 下载。

如需我根据实际运行情况补充“可操作流程清单/故障排查手册”，告诉我即可补充。
