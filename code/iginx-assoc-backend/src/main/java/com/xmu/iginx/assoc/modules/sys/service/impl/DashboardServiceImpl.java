package com.xmu.iginx.assoc.modules.sys.service.impl;

import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.model.entity.MetaModelProfileEntity;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.repository.MetaModelProfileRepository;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import com.xmu.iginx.assoc.modules.sys.service.DashboardService;
import com.xmu.iginx.assoc.modules.sys.vo.DashboardRecentTaskVO;
import com.xmu.iginx.assoc.modules.sys.vo.DashboardSummaryVO;
import com.xmu.iginx.assoc.modules.sys.vo.DashboardTrendPointVO;
import com.xmu.iginx.assoc.modules.task.entity.TaskEntity;
import com.xmu.iginx.assoc.modules.task.enums.TaskStatus;
import com.xmu.iginx.assoc.modules.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仪表盘服务实现，汇总系统统计数据。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int TREND_DAYS = 7;
    private static final int RECENT_TASK_LIMIT = 8;
    private static final DateTimeFormatter TREND_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TASK_NAME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TaskRepository taskRepository;
    private final AssociationRuleRepository associationRuleRepository;
    private final DataResourceRepository dataResourceRepository;
    private final MetaModelProfileRepository profileRepository;
    private final ModelAssetRepository modelAssetRepository;

    /**
     * 汇总系统统计数据，包含任务趋势与最近任务。
     *
     * @return 总览数据
     */
    @Override
    public DashboardSummaryVO fetchSummary() {
        DashboardSummaryVO summary = new DashboardSummaryVO();
        summary.setModelCount(profileRepository.count());
        summary.setRuleCount(associationRuleRepository.count());
        summary.setDataSourceCount(dataResourceRepository.count());
        summary.setTaskCount(taskRepository.count());
        summary.setRunningTaskCount(taskRepository.countByStatus(TaskStatus.RUNNING.name()));
        summary.setSuccessTaskCount(taskRepository.countByStatus(TaskStatus.SUCCESS.name()));
        summary.setFailedTaskCount(taskRepository.countByStatus(TaskStatus.FAILED.name()));
        summary.setTaskTrend(buildTaskTrend());
        summary.setRecentTasks(buildRecentTasks());
        return summary;
    }

    /**
     * 生成近一周任务趋势数据。
     */
    private List<DashboardTrendPointVO> buildTaskTrend() {
        // 计算近 7 天时间范围
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(TREND_DAYS - 1);
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();

        List<TaskEntity> tasks = taskRepository.findByCreateTimeBetween(startTime, endTime);
        Map<LocalDate, List<TaskEntity>> dayMap = new HashMap<>();
        for (TaskEntity task : tasks) {
            if (task.getCreateTime() == null) {
                continue;
            }
            LocalDate day = task.getCreateTime().toLocalDate();
            dayMap.computeIfAbsent(day, key -> new ArrayList<>()).add(task);
        }

        List<DashboardTrendPointVO> trend = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = startDate.plusDays(i);
            List<TaskEntity> dayTasks = dayMap.getOrDefault(day, List.of());
            DashboardTrendPointVO point = new DashboardTrendPointVO();
            point.setDate(day.format(TREND_DATE_FORMATTER));
            point.setTaskCount((long) dayTasks.size());
            point.setAvgDurationSec(calculateAvgDuration(dayTasks));
            trend.add(point);
        }
        return trend;
    }

    /**
     * 计算任务平均耗时（秒）。
     *
     * @param tasks 任务列表
     * @return 平均耗时；无有效数据时返回 null
     */
    private Double calculateAvgDuration(List<TaskEntity> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        long sum = 0;
        long count = 0;
        for (TaskEntity task : tasks) {
            LocalDateTime start = task.getStartTime();
            LocalDateTime end = task.getEndTime();
            if (start == null || end == null) {
                continue;
            }
            if (end.isBefore(start)) {
                continue;
            }
            long seconds = Duration.between(start, end).getSeconds();
            sum += seconds;
            count++;
        }
        if (count == 0) {
            return null;
        }
        return sum / (double) count;
    }

    /**
     * 构建最近任务列表（包含规则/模型等信息）。
     */
    private List<DashboardRecentTaskVO> buildRecentTasks() {
        List<TaskEntity> recentTasks = taskRepository
            .findAll(PageRequest.of(0, RECENT_TASK_LIMIT, Sort.by(Sort.Direction.DESC, "createTime")))
            .getContent();
        if (recentTasks.isEmpty()) {
            return List.of();
        }

        // 预加载规则
        Set<Long> ruleIds = recentTasks.stream()
            .map(TaskEntity::getRuleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, AssociationRuleEntity> ruleMap = associationRuleRepository.findAllById(ruleIds)
            .stream()
            .collect(Collectors.toMap(AssociationRuleEntity::getId, item -> item));

        // 预加载模型资产
        Set<Long> modelIds = ruleMap.values().stream()
            .map(AssociationRuleEntity::getModelId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ModelAssetEntity> modelMap = modelAssetRepository.findAllById(modelIds)
            .stream()
            .collect(Collectors.toMap(ModelAssetEntity::getId, item -> item));

        // 预加载模型档案
        Set<Long> profileIds = new HashSet<>();
        for (ModelAssetEntity asset : modelMap.values()) {
            if (asset.getProfileId() != null) {
                profileIds.add(asset.getProfileId());
            }
        }
        Map<Long, MetaModelProfileEntity> profileMap = profileIds.isEmpty()
            ? Map.of()
            : profileRepository.findAllById(profileIds)
            .stream()
            .collect(Collectors.toMap(MetaModelProfileEntity::getId, item -> item));

        List<DashboardRecentTaskVO> result = new ArrayList<>();
        for (TaskEntity task : recentTasks) {
            AssociationRuleEntity rule = ruleMap.get(task.getRuleId());
            ModelAssetEntity asset = rule == null ? null : modelMap.get(rule.getModelId());
            MetaModelProfileEntity profile = asset == null ? null : profileMap.get(asset.getProfileId());

            DashboardRecentTaskVO vo = new DashboardRecentTaskVO();
            vo.setId(task.getId());
            vo.setTaskName(resolveTaskName(task, rule));
            vo.setRuleName(rule == null ? "-" : rule.getName());
            vo.setModelType(asset == null ? "-" : asset.getFileType());
            vo.setModelName(profile == null ? "-" : profile.getName());
            vo.setStatus(task.getStatus());
            vo.setStartTime(task.getStartTime());
            vo.setEndTime(task.getEndTime());
            vo.setCreateTime(task.getCreateTime());
            vo.setDurationSec(calculateDuration(task.getStartTime(), task.getEndTime()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 计算单个任务耗时（秒）。
     *
     * @param start 开始时间
     * @param end 结束时间
     * @return 耗时秒数；时间不合法时返回 null
     */
    private Long calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        if (end.isBefore(start)) {
            return null;
        }
        return Duration.between(start, end).getSeconds();
    }

    /**
     * 解析最近任务的展示名称，兼容旧任务未保存名称的情况。
     */
    private String resolveTaskName(TaskEntity task, AssociationRuleEntity rule) {
        if (task != null && StringUtils.hasText(task.getTaskName())) {
            return task.getTaskName().trim();
        }
        String baseName = rule != null && StringUtils.hasText(rule.getName()) ? rule.getName().trim() : "任务";
        if (task != null && task.getCreateTime() != null) {
            return baseName + "_" + task.getCreateTime().format(TASK_NAME_TIME_FORMATTER);
        }
        if (task != null && StringUtils.hasText(task.getId())) {
            String shortId = task.getId().length() > 8 ? task.getId().substring(0, 8) : task.getId();
            return baseName + "_" + shortId;
        }
        return baseName;
    }
}
