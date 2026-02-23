package com.xmu.iginx.assoc.modules.data.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ExcelRowListener extends AnalysisEventListener<Map<Integer, String>> {

    private final BiConsumer<List<String>, Boolean> rowConsumer;
    private List<String> header = new ArrayList<>();

    public ExcelRowListener(BiConsumer<List<String>, Boolean> rowConsumer) {
        this.rowConsumer = rowConsumer;
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        header = new ArrayList<>();
        int max = headMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int i = 0; i <= max; i++) {
            header.add(headMap.getOrDefault(i, ""));
        }
        rowConsumer.accept(header, true);
    }

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        List<String> row = new ArrayList<>();
        int max = data.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int i = 0; i <= max; i++) {
            row.add(data.getOrDefault(i, ""));
        }
        rowConsumer.accept(row, false);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
    }
}
