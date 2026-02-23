package com.xmu.iginx.assoc.modules.analysis.service;

import com.xmu.iginx.assoc.modules.analysis.dto.TaskCompareRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskExportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskReportRequest;
import com.xmu.iginx.assoc.modules.analysis.dto.TaskSeriesRequest;
import com.xmu.iginx.assoc.modules.analysis.vo.TaskSeriesVO;

import java.util.List;

public interface AnalysisService {

    List<TaskSeriesVO> queryTaskSeries(String taskId, TaskSeriesRequest request);

    List<TaskSeriesVO> compareTasks(TaskCompareRequest request);

    String exportPackage(String taskId, TaskExportRequest request);

    String generateReport(String taskId, TaskReportRequest request);
}
