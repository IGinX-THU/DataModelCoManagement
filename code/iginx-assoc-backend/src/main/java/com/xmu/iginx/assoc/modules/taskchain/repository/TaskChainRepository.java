package com.xmu.iginx.assoc.modules.taskchain.repository;

import com.xmu.iginx.assoc.modules.taskchain.entity.TaskChainEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 任务链定义仓库。
 */
public interface TaskChainRepository extends JpaRepository<TaskChainEntity, Long> {
}
