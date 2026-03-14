package com.xmu.iginx.assoc.modules.analysis.service;

import com.xmu.iginx.assoc.modules.analysis.dto.TaskCompareRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskExportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskReportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskSeriesRequest;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskSeriesVO;

import java.util.List;

/**
 * 分析模块服务接口。
 */
public interface AnalysisService {

    /**
     * 查询任务时序曲线数据。
     *
     * @param taskId 任务 ID
     * @param request 曲线请求参数
     * @return 曲线数据列表
     */
    List<TaskSeriesVO> queryTaskSeries(String taskId, TaskSeriesRequest request);

    /**
     * 对多个任务进行曲线对比。
     *
     * @param request 对比请求参数
     * @return 曲线数据列表
     */
    List<TaskSeriesVO> compareTasks(TaskCompareRequest request);

    /**
     * 导出任务资源包。
     *
     * @param taskId 任务 ID
     * @param request 导出参数
     * @return 下载路径
     */
    String exportPackage(String taskId, TaskExportRequest request);

    /**
     * 生成任务实验报告。
     *
     * @param taskId 任务 ID
     * @param request 报告参数
     * @return 下载路径
     */
    String generateReport(String taskId, TaskReportRequest request);
}
