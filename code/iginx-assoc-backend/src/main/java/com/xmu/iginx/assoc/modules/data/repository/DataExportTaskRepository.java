package com.xmu.iginx.assoc.modules.data.repository;

import com.xmu.iginx.assoc.modules.data.entity.DataExportTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataExportTaskRepository extends JpaRepository<DataExportTaskEntity, Long> {
}
