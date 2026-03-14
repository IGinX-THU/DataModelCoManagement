package com.xmu.iginx.assoc.modules.external.repository;

import com.xmu.iginx.assoc.modules.external.entity.ExternalJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 外部任务数据访问接口。
 */
public interface ExternalJobRepository extends JpaRepository<ExternalJobEntity, String> {
}
