package com.xmu.iginx.assoc.modules.data.vo;

import com.xmu.iginx.assoc.common.PageResult;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 结构化查询结果视图对象。
 */
@Data
public class StructuredQueryResultVO {

    /**
     * 列名列表（按展示顺序）。
     */
    private List<String> columns;

    /**
     * 分页数据（每行使用字段名 -> 值的映射）。
     */
    private PageResult<Map<String, Object>> page;
}
