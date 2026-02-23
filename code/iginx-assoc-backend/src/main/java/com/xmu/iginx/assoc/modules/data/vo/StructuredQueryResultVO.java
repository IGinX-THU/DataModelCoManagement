package com.xmu.iginx.assoc.modules.data.vo;

import com.xmu.iginx.assoc.common.PageResult;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StructuredQueryResultVO {

    private List<String> columns;

    private PageResult<Map<String, Object>> page;
}
