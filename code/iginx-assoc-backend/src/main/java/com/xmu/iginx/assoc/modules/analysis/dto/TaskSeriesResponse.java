package com.xmu.iginx.assoc.modules.analysis.dto;

import com.xmu.iginx.assoc.modules.analysis.vo.TaskSeriesVO;
import lombok.Data;

import java.util.List;

@Data
public class TaskSeriesResponse {

    private List<TaskSeriesVO> series;
}
