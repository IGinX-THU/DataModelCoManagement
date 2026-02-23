package com.xmu.iginx.assoc.modules.task.repository;

import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    List<TaskEntity> findByRuleIdOrderByCreateTimeDesc(Long ruleId);

    List<TaskEntity> findByCreateTimeBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(String status);

    boolean existsByRuleIdAndStatusIn(Long ruleId, Iterable<String> statuses);

    void deleteByRuleId(Long ruleId);
}
