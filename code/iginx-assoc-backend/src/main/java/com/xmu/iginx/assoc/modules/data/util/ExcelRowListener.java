package com.xmu.iginx.assoc.modules.data.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Excel 解析监听器，将表头与数据行回调给上层处理。
 */
public class ExcelRowListener extends AnalysisEventListener<Map<Integer, String>> {

    private final BiConsumer<List<String>, Boolean> rowConsumer;
    private List<String> header = new ArrayList<>();

    /**
     * 构造监听器。
     *
     * @param rowConsumer 行回调（true 表示表头）
     */
    public ExcelRowListener(BiConsumer<List<String>, Boolean> rowConsumer) {
        this.rowConsumer = rowConsumer;
    }

    @Override
    /**
     * 读取表头。
     *
     * @param headMap 表头映射
     * @param context 解析上下文
     */
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        header = new ArrayList<>();
        int max = headMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int i = 0; i <= max; i++) {
            header.add(headMap.getOrDefault(i, ""));
        }
        // 将表头交给回调处理
        rowConsumer.accept(header, true);
    }

    @Override
    /**
     * 读取数据行。
     *
     * @param data 行数据映射
     * @param context 解析上下文
     */
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        List<String> row = new ArrayList<>();
        int max = data.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int i = 0; i <= max; i++) {
            row.add(data.getOrDefault(i, ""));
        }
        // 将数据行交给回调处理
        rowConsumer.accept(row, false);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 无需额外处理
    }
}
