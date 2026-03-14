package com.xmu.iginx.assoc.modules.data.repository;

import com.xmu.iginx.assoc.modules.data.entity.DataExportTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 数据导出任务仓储。
 */
public interface DataExportTaskRepository extends JpaRepository<DataExportTaskEntity, Long> {
}
