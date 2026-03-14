# 注释与乱码修复 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 `src/` 下所有方法补充中文注释并修复中文转义乱码，排除构造函数与 getter/setter。

**Architecture:** 逐模块人工阅读与编辑，先清理 `\\uXXXX` 转义，再补齐方法级 Javadoc 与复杂逻辑行内注释，保持业务逻辑不变。

**Tech Stack:** Java、Spring Boot、Maven、Spring Data JPA（Repository 层）。

---

## 前置说明

- 当前目录未检测到 Git 仓库，无法创建工作树与提交记录。若需要 Git 流程，请先初始化仓库。
- `rg` 在当前环境不可用，将使用 PowerShell `Get-ChildItem` + `Select-String` 进行扫描。
- 仅处理 `.java` 源码文件，不处理 `*.java.bak` 备份文件。

## 注释模板示例

```java
/**
 * 用途：xxx。
 *
 * @param request 请求参数，包含 xxx 信息
 * @return 处理结果，包含 xxx
 * @throws BizException 当 xxx 时抛出
 */
public Result<XXX> doSomething(Request request) {
    // 关键判断：xxx
    if (condition) {
        // 为什么这样做：xxx
        ...
    }
    return result;
}
```

## 通用步骤（适用于后续各任务）

**Step 1: 打开并逐文件阅读**

逐个打开本任务文件列表，理解方法职责与关键逻辑。

**Step 2: 修复 `\\uXXXX` 转义**

若发现注解/字符串中存在转义，按上下文还原为正确中文。

**Step 3: 添加方法头 Javadoc**

覆盖所有业务方法（排除构造函数、getter、setter），描述用途、参数、返回值、异常。

**Step 4: 添加复杂逻辑行内注释**

在关键分支、算法步骤、资源处理处补充“为什么这样做”的中文注释。

**Step 5: 自检**

确认注释与实现一致，不引入歧义或空话。

**Step 6: 提交（若 Git 可用）**

```bash
git add .
git commit -m "docs: 添加中文注释并修复中文转义"
```

---

### Task 1: 生成乱码清单与确认修改范围

**Files:**
- Modify: 无

**Step 1: 列出所有 Java 文件**

```powershell
Get-ChildItem -Recurse -File -Path "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src" -Filter "*.java"
```

Expected: 输出 `src/main/java` 与 `src/test/java` 下所有 `.java` 文件。

**Step 2: 扫描 `\\uXXXX` 转义**

```powershell
Get-ChildItem -Recurse -File -Path "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src" -Filter "*.java" | Select-String -Pattern "\\\\u[0-9a-fA-F]{4}"
```

Expected: 输出包含转义的文件路径与行号（若无输出则表示未发现）。

**Step 3: 批量修改前确认**

在开始批量注释前，依据“危险操作确认机制”向用户确认。

---

### Task 2: common 与 framework 包注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/IginxAssocApplication.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/common/PageResult.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/common/Result.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/common/exception/BizException.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/common/exception/GlobalExceptionHandler.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/framework/crypto/CryptoConfig.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/framework/iginx/IginxConfig.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/framework/iginx/IginxFileSystemConfig.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/framework/iginx/IginxFileSystemRegistrar.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/framework/iginx/IginxStorageWrapper.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/framework/security/SecurityConfig.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/framework/storage/StorageConfig.java`

按通用步骤执行。

---

### Task 3: analysis 模块注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/controller/AnalysisController.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/dto/TaskCompareRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/dto/TaskExportRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/dto/TaskReportRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/dto/TaskSeriesRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/dto/TaskSeriesResponse.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/service/AnalysisService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/service/impl/AnalysisServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/util/ReportPdfBuilder.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/vo/TaskSeriesPointVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/analysis/vo/TaskSeriesVO.java`

按通用步骤执行。

---

### Task 4: data 模块 Controller 注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/controller/DataResourceController.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/controller/DataSourceController.java`

按通用步骤执行。

---

### Task 5: data 模块 DTO 注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/DataExportRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/DataSourceConnectionConfig.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/DataSourceCreateRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/DataSourceQueryRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/DataSourceUpdateRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/MeasurementRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/StorageGroupRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/StructuredImportRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/StructuredQueryCondition.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/StructuredQueryRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/StructuredRowCreateRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/StructuredRowDeleteRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/StructuredRowUpdateRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/TableColumnDefinitionDTO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/TableCreateRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/TableDropRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/TimeRangeDTO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/TimeSeriesColumnMappingDTO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/TimeSeriesDeleteRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/TimeSeriesImportRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/dto/TimeSeriesQueryRequest.java`

按通用步骤执行。

---

### Task 6: data 模块 Entity/Enum/Model 注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/entity/DataExportTaskEntity.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/entity/DataResourceEntity.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/enums/DataExportTaskStatus.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/enums/DataSourceType.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/model/DataSourceDetail.java`

按通用步骤执行。

---

### Task 7: data 模块 Repository/Service 接口注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/repository/DataExportTaskRepository.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/repository/DataResourceRepository.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/DataExportService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/DataImportService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/DataMaintainService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/DataQueryService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/DataSourceAccessor.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/DataSourceConnectionTestService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/DataSourceService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/StructureService.java`

按通用步骤执行。

---

### Task 8: data 模块 Service 实现注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataExportServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataImportServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataMaintainServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataQueryServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataSourceServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/StructureServiceImpl.java`

按通用步骤执行。

---

### Task 9: data 模块 Util 注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/ConnectionConfigCipher.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/CsvUtils.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/DataFileStorageService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/ExcelRowListener.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/IginxDataTypeConverter.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/IginxStorageEngineHelper.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/IginxStructuredQueryHelper.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/IginxStructuredUtils.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/JdbcMetadataUtils.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/JdbcValueConverter.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/RelationalConnectionFactory.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/StructuredKeyGenerator.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/StructuredSqlBuilder.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/TimeParser.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/util/TimeSeriesPathUtils.java`

按通用步骤执行。

---

### Task 10: data 模块 VO 注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/vo/DataExportResultVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/vo/DataImportResultVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/vo/DataSourceConnectionConfigVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/vo/DataSourceStructureNodeVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/vo/DataSourceVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/vo/StructuredQueryResultVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/vo/TableColumnVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/vo/TimeSeriesQueryResultVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/vo/TimeSeriesSeriesVO.java`

按通用步骤执行。

---

### Task 11: external 模块注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/controller/ExternalJobController.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/dto/ExternalAlgorithmJobRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/dto/ExternalDataExportJobRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/dto/ExternalDataImportJobRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/dto/ExternalJobCreateRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/dto/ExternalModelJobRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/entity/ExternalJobEntity.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/enums/ExternalJobStatus.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/enums/ExternalJobType.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/repository/ExternalJobRepository.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/service/ExternalJobService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/service/impl/ExternalJobServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/util/PathMultipartFile.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/vo/ExternalErrorResponse.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/vo/ExternalJobCreateResponse.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/vo/ExternalJobResultResponse.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/external/vo/ExternalJobStatusResponse.java`

按通用步骤执行。

---

### Task 12: model 模块注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/controller/ModelAssetController.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/dto/ModelIoSchema.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/dto/ModelProfileUpdateRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/dto/ModelSchemaParam.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/dto/ModelUploadRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/entity/MetaModelProfileEntity.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/entity/ModelAssetEntity.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/repository/MetaModelProfileRepository.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/repository/ModelAssetRepository.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/service/ModelAssetService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/service/impl/ModelAssetServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/util/ModelFileStorageService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/util/ModelFunctionSchemaParser.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/util/ModelSchemaParser.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/vo/ModelFunctionOptionVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/vo/ModelProfileVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/vo/ModelSchemaParseVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/model/vo/ModelVersionVO.java`

按通用步骤执行。

---

### Task 13: relation 模块注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/relation/controller/AssociationRuleController.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/relation/dto/AssociationRuleCreateRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/relation/dto/AssociationRuleUpdateRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/relation/dto/RuleStatusRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/relation/entity/AssociationRuleEntity.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/relation/repository/AssociationRuleRepository.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/relation/service/AssociationRuleService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/relation/service/impl/AssociationRuleServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/relation/vo/AssociationRuleVO.java`

按通用步骤执行。

---

### Task 14: sys 模块注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/controller/DashboardController.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/controller/HomeController.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/controller/SystemController.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/dto/SqlExecuteRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/service/DashboardService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/service/SystemLogBuffer.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/service/SystemLogService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/service/SystemSqlService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/service/impl/DashboardServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/service/impl/SystemLogServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/service/impl/SystemSqlServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/vo/DashboardRecentTaskVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/vo/DashboardSummaryVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/vo/DashboardTrendPointVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/vo/SqlExecuteResultVO.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/sys/vo/SystemLogEntryVO.java`

按通用步骤执行。

---

### Task 15: task 模块注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/task/controller/TaskController.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/task/dto/TaskSubmitRequest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/task/entity/TaskEntity.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/task/enums/TaskStatus.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/task/repository/TaskRepository.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/task/service/TaskScheduler.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/task/service/TaskService.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/task/service/impl/TaskServiceImpl.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/task/vo/TaskVO.java`

按通用步骤执行。

---

### Task 16: src/test 测试代码注释与乱码修复

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/test/java/com/xmu/iginx/assoc/modules/data/service/impl/DataExportServiceImplTest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/test/java/com/xmu/iginx/assoc/modules/external/service/impl/ExternalJobServiceImplTest.java`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src/test/java/com/xmu/iginx/assoc/modules/model/util/ModelFunctionSchemaParserTest.java`

按通用步骤执行，仅添加说明性注释，不改断言与测试逻辑。

---

### Task 17: 全局复核与可选测试

**Files:**
- Modify: 无

**Step 1: 复扫 `\\uXXXX`**

```powershell
Get-ChildItem -Recurse -File -Path "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code未修改元数据/iginx-assoc-backend/src" -Filter "*.java" | Select-String -Pattern "\\\\u[0-9a-fA-F]{4}"
```

Expected: 无输出。

**Step 2: 运行现有单测（可选）**

```bash
mvn -q -Dtest=DataExportServiceImplTest,ExternalJobServiceImplTest,ModelFunctionSchemaParserTest test
```

Expected: BUILD SUCCESS；若因外部依赖失败，记录失败原因并停止继续。

**Step 3: 最终复核**

抽查关键 Controller/Service 方法注释是否准确、避免空话。
