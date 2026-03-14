package com.xmu.iginx.assoc.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果统一封装。
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records;
    private long total;
    private int pageNum;
    private int pageSize;

    /**
     * 生成分页结果对象。
     *
     * @param records 当前页记录
     * @param total 总记录数
     * @param pageNum 当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @param <T> 记录类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setTotal(total);
        pageResult.setPageNum(pageNum);
        pageResult.setPageSize(pageSize);
        return pageResult;
    }
}
