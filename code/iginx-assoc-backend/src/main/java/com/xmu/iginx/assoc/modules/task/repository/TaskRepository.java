package com.xmu.iginx.assoc.modules.task.repository;

import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务数据访问接口。
 */
public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    /**
     * 按规则 ID 查询任务列表（按创建时间倒序）。
     *
     * @param ruleId 规则 ID
     * @return 任务列表
     */
    List<TaskEntity> findByRuleIdOrderByCreateTimeDesc(Long ruleId);

    /**
     * 按创建时间范围查询任务列表。
     *
     * @param start 开始时间
     * @param end 结束时间
     * @return 任务列表
     */
    List<TaskEntity> findByCreateTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 按状态统计任务数量。
     *
     * @param status 状态
     * @return 数量
     */
    long countByStatus(String status);

    /**
     * 判断规则是否存在指定状态的任务。
     *
     * @param ruleId 规则 ID
     * @param statuses 状态集合
     * @return 是否存在
     */
    boolean existsByRuleIdAndStatusIn(Long ruleId, Iterable<String> statuses);

    /**
     * 删除指定规则的任务。
     *
     * @param ruleId 规则 ID
     */
    void deleteByRuleId(Long ruleId);
}
