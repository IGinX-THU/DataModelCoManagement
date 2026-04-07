package com.xmu.iginx.assoc.modules.taskchain.repository;

import com.xmu.iginx.assoc.modules.taskchain.entity.TaskChainRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 任务链运行记录仓库。
 */
public interface TaskChainRunRepository extends JpaRepository<TaskChainRunEntity, String> {

    /**
     * 按任务链查询运行记录。
     */
    List<TaskChainRunEntity> findByChainIdOrderByCreateTimeDesc(Long chainId);

    /**
     * 判断任务链是否存在指定状态的运行记录。
     */
    boolean existsByChainIdAndStatusIn(Long chainId, Collection<String> statuses);

    /**
     * 删除任务链下的全部运行记录。
     */
    void deleteByChainId(Long chainId);
}
