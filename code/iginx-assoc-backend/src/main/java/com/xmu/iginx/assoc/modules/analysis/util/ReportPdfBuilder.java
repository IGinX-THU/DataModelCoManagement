package com.xmu.iginx.assoc.modules.analysis.util;

import lombok.Data;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 报告 PDF 构建器，负责将报告内容渲染为 PDF 字节。
 */
public class ReportPdfBuilder {

    private static final float PAGE_WIDTH = 595f; // A4 宽度
    private static final float PAGE_HEIGHT = 842f; // A4 高度
    private static final float MARGIN_X = 40f;
    private static final float MARGIN_TOP = 40f;
    private static final float MARGIN_BOTTOM = 40f;
    private static final DateTimeFormatter TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * 报告内容模型。
     */
    @Data
    public static class ReportContent {
        private String title = "Experiment Report";
        private String analysisMode = "TIME_SERIES";
        private String generatedAt = "-";
        private String taskId = "-";
        private String ruleName = "-";
        private String modelName = "-";
        private String modelVersion = "-";
        private String executor = "-";
        private String createTime = "-";
        private String rangeStart = "-";
        private String rangeEnd = "-";
        private String startTime = "-";
        private String endTime = "-";
        private List<String> outputPaths = List.of();
        private List<MetricRow> metrics = List.of();
        private List<TableBlock> tableBlocks = List.of();
        private ChartData chartData;
        private boolean includeStats = true;
        private boolean includeCharts = true;
    }

    /**
     * 指标表格行。
     *
     * @param path 路径
     * @param count 数量
     * @param min 最小值
     * @param max 最大值
     * @param avg 平均值
     */
    public record MetricRow(String path, long count, Double min, Double max, Double avg) {
    }

    /**
     * 图表序列数据。
     *
     * @param name 序列名称
     * @param values 序列值
     */
    public record ChartSeries(String name, List<Double> values) {
    }

    /**
     * 图表数据。
     *
     * @param timestamps 时间戳列表
     * @param series 曲线序列
     * @param min 最小值
     * @param max 最大值
     */
    public record ChartData(List<Long> timestamps, List<ChartSeries> series, Double min, Double max) {
    }

    /**
     * 结构化表格预览块。
     *
     * @param title 标题
     * @param columns 列
     * @param rows 行
     */
    public record TableBlock(String title, List<String> columns, List<java.util.Map<String, Object>> rows) {
    }

    /**
     * 构建 PDF 字节。
     *
     * @param content 报告内容
     * @return PDF 字节数组
     */
    public byte[] build(ReportContent content) {
        PdfDocument doc = new PdfDocument(PAGE_WIDTH, PAGE_HEIGHT);
        Layout layout = new Layout(doc);
        layout.render(content);
        doc.appendPageNumbers();
        return doc.build();
    }

    private static final class Layout {
        private final PdfDocument doc;
        private float cursorY = MARGIN_TOP;

        private Layout(PdfDocument doc) {
            this.doc = doc;
        }

        /**
         * 渲染完整报告内容。
         *
         * @param content 报告内容
         */
        private void render(ReportContent content) {
            drawTitle(content);
            drawOverview(content);
            if (content.isIncludeStats()) {
                drawStats(content.getMetrics());
            }
            drawTableBlocks(content.getTableBlocks());
            if (content.isIncludeCharts()) {
                drawCharts(content.getChartData());
            }
        }

        /**
         * 绘制标题与生成时间。
         *
         * @param content 报告内容
         */
        private void drawTitle(ReportContent content) {
            ensureSpace(64);
            String title = safeText(content.getTitle(), "Experiment Report");
            float titleSize = 20f;
            float titleWidth = doc.estimateTextWidth(title, titleSize);
            float titleX = (PAGE_WIDTH - titleWidth) / 2f;
            doc.setFillColor(0.12f, 0.16f, 0.22f);
            doc.drawText(titleX, cursorY + 22f, (int) titleSize, title, true);
            cursorY += 32f;

            doc.setFillColor(0.4f, 0.4f, 0.4f);
            doc.drawText(MARGIN_X, cursorY + 12f, 10, "Generated at: " + safeText(content.getGeneratedAt(), "-"), false);
            cursorY += 20f;

            doc.setStrokeColor(0.85f, 0.85f, 0.85f);
            doc.setLineWidth(0.6f);
            doc.drawLine(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY);
            cursorY += 16f;
        }

        /**
         * 绘制任务概览。
         *
         * @param content 报告内容
         */
        private void drawOverview(ReportContent content) {
            drawSectionTitle("Task Overview");
            drawKeyValue("Task ID", content.getTaskId());
            drawKeyValue("Rule", content.getRuleName());
            drawKeyValue("Analysis Mode", content.getAnalysisMode());
            drawKeyValue("Model", content.getModelName());
            drawKeyValue("Model Version", content.getModelVersion());
            drawKeyValue("Executor", content.getExecutor());
            drawKeyValue("Create Time", content.getCreateTime());
            drawKeyValue("Execute Start", content.getStartTime());
            drawKeyValue("Execute End", content.getEndTime());
            drawKeyValue("Data Range Start", content.getRangeStart());
            drawKeyValue("Data Range End", content.getRangeEnd());
            if (content.getOutputPaths() == null || content.getOutputPaths().isEmpty()) {
                drawKeyValue("Output Paths", "-");
            } else {
                drawKeyValue("Output Paths", String.join(", ", content.getOutputPaths()));
            }
        }

        /**
         * 绘制结构化表格预览块。
         *
         * @param tableBlocks 表格块列表
         */
        private void drawTableBlocks(List<TableBlock> tableBlocks) {
            if (tableBlocks == null || tableBlocks.isEmpty()) {
                return;
            }
            for (TableBlock block : tableBlocks) {
                drawSectionTitle(block.title());
                drawDataTable(block.columns(), block.rows());
            }
        }

        /**
         * 绘制普通数据表格。
         *
         * @param columns 列集合
         * @param rows 行集合
         */
        private void drawDataTable(List<String> columns, List<java.util.Map<String, Object>> rows) {
            if (columns == null || columns.isEmpty() || rows == null || rows.isEmpty()) {
                drawNote("No table data available.");
                return;
            }
            int columnCount = Math.max(1, columns.size());
            float contentWidth = PAGE_WIDTH - MARGIN_X * 2f;
            float[] colWidths = new float[columnCount];
            float columnWidth = contentWidth / columnCount;
            for (int index = 0; index < columnCount; index++) {
                colWidths[index] = columnWidth;
            }
            drawTableHeader(colWidths, columns.toArray(String[]::new));

            float rowHeight = 18f;
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                if (cursorY + rowHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    doc.newPage();
                    cursorY = MARGIN_TOP;
                    drawSectionTitle("Table Preview (cont.)");
                    drawTableHeader(colWidths, columns.toArray(String[]::new));
                }
                if (rowIndex % 2 == 1) {
                    doc.setFillColor(0.97f, 0.98f, 0.99f);
                    doc.drawRect(MARGIN_X, cursorY, contentWidth, rowHeight, true, false);
                }
                doc.setStrokeColor(0.9f, 0.9f, 0.9f);
                doc.setLineWidth(0.4f);
                doc.drawRect(MARGIN_X, cursorY, contentWidth, rowHeight, false, true);

                float x = MARGIN_X + 4f;
                float textY = cursorY + 13f;
                java.util.Map<String, Object> row = rows.get(rowIndex);
                for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                    String column = columns.get(colIndex);
                    String cell = row == null ? "-" : safeText(String.valueOf(row.getOrDefault(column, "-")), "-");
                    doc.setFillColor(0.25f, 0.25f, 0.25f);
                    doc.drawText(x, textY, 9, truncate(cell, (int) maxChars(colWidths[colIndex] - 6f, 9)), false);
                    x += colWidths[colIndex];
                }
                cursorY += rowHeight;
            }
            cursorY += 8f;
        }

        /**
         * 绘制统计表格。
         *
         * @param metrics 指标列表
         */
        private void drawStats(List<MetricRow> metrics) {
            drawSectionTitle("Statistics");
            if (metrics == null || metrics.isEmpty()) {
                drawNote("No statistics available.");
                return;
            }
            float contentWidth = PAGE_WIDTH - MARGIN_X * 2f;
            float numericWidth = 60f;
            float pathWidth = contentWidth - numericWidth * 4f;
            float[] colWidths = new float[] { pathWidth, numericWidth, numericWidth, numericWidth, numericWidth };
            String[] headers = new String[] { "Path", "Count", "Min", "Max", "Avg" };

            drawTableHeader(colWidths, headers);
            float rowHeight = 18f;
            int rowIndex = 0;
            for (MetricRow row : metrics) {
                if (cursorY + rowHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    doc.newPage();
                    cursorY = MARGIN_TOP;
                    drawSectionTitle("Statistics (cont.)");
                    drawTableHeader(colWidths, headers);
                }
                boolean zebra = rowIndex % 2 == 1;
                if (zebra) {
                    doc.setFillColor(0.97f, 0.98f, 0.99f);
                    doc.drawRect(MARGIN_X, cursorY, contentWidth, rowHeight, true, false);
                }
                doc.setStrokeColor(0.9f, 0.9f, 0.9f);
                doc.setLineWidth(0.4f);
                doc.drawRect(MARGIN_X, cursorY, contentWidth, rowHeight, false, true);

                float x = MARGIN_X + 4f;
                float textY = cursorY + 13f;
                doc.setFillColor(0.25f, 0.25f, 0.25f);
                doc.drawText(x, textY, 10, truncate(row.path(), (int) maxChars(pathWidth, 10)), false);
                x += colWidths[0];
                doc.drawText(x + 4f, textY, 10, String.valueOf(row.count()), false);
                x += colWidths[1];
                doc.drawText(x + 4f, textY, 10, formatNumber(row.min()), false);
                x += colWidths[2];
                doc.drawText(x + 4f, textY, 10, formatNumber(row.max()), false);
                x += colWidths[3];
                doc.drawText(x + 4f, textY, 10, formatNumber(row.avg()), false);

                cursorY += rowHeight;
                rowIndex++;
            }
            cursorY += 8f;
        }

        /**
         * 绘制图表区域。
         *
         * @param chartData 图表数据
         */
        private void drawCharts(ChartData chartData) {
            drawSectionTitle("Charts");
            if (chartData == null || chartData.timestamps() == null
                || chartData.timestamps().isEmpty() || chartData.series() == null || chartData.series().isEmpty()) {
                drawNote("No chart data available.");
                return;
            }
            float chartHeight = 260f;
            ensureSpace(chartHeight + 8f);
            float chartX = MARGIN_X;
            float chartY = cursorY;
            float chartW = PAGE_WIDTH - MARGIN_X * 2f;
            float chartH = chartHeight;
            drawChart(chartX, chartY, chartW, chartH, chartData);
            cursorY += chartHeight + 12f;
        }

        /**
         * 绘制单个图表。
         *
         * @param x 左上角 X
         * @param y 左上角 Y
         * @param width 宽度
         * @param height 高度
         * @param chartData 图表数据
         */
        private void drawChart(float x, float y, float width, float height, ChartData chartData) {
            doc.setStrokeColor(0.85f, 0.85f, 0.85f);
            doc.setLineWidth(0.6f);
            doc.drawRect(x, y, width, height, false, true);

            float paddingLeft = 45f;
            float paddingRight = 10f;
            float paddingTop = 14f;
            float paddingBottom = 30f;

            float plotX = x + paddingLeft;
            float plotY = y + paddingTop;
            float plotW = width - paddingLeft - paddingRight;
            float plotH = height - paddingTop - paddingBottom;

            Double minVal = chartData.min();
            Double maxVal = chartData.max();
            if (minVal == null || maxVal == null) {
                minVal = 0d;
                maxVal = 1d;
            }
            double range = maxVal - minVal;
            if (range == 0) {
                range = 1d;
                minVal -= 0.5d;
                maxVal += 0.5d;
            }

            // 绘制网格线
            int gridCount = 4;
            doc.setStrokeColor(0.92f, 0.92f, 0.92f);
            doc.setLineWidth(0.4f);
            for (int i = 0; i <= gridCount; i++) {
                float gy = plotY + (plotH / gridCount) * i;
                doc.drawLine(plotX, gy, plotX + plotW, gy);
            }
            for (int i = 0; i <= gridCount; i++) {
                float gx = plotX + (plotW / gridCount) * i;
                doc.drawLine(gx, plotY, gx, plotY + plotH);
            }

            doc.setStrokeColor(0.6f, 0.6f, 0.6f);
            doc.setLineWidth(0.8f);
            doc.drawLine(plotX, plotY, plotX, plotY + plotH);
            doc.drawLine(plotX, plotY + plotH, plotX + plotW, plotY + plotH);

            // 轴标签
            doc.setFillColor(0.35f, 0.35f, 0.35f);
            doc.drawText(plotX - 38f, plotY + 8f, 9, formatNumber(maxVal), false);
            doc.drawText(plotX - 38f, plotY + plotH + 8f, 9, formatNumber(minVal), false);

            List<Long> timestamps = chartData.timestamps();
            long start = timestamps.get(0);
            long end = timestamps.get(timestamps.size() - 1);
            doc.drawText(plotX, plotY + plotH + 20f, 9, formatTimeLabel(start), false);
            doc.drawText(plotX + plotW - 80f, plotY + plotH + 20f, 9, formatTimeLabel(end), false);

            float[][] palette = new float[][] {
                {0.23f, 0.49f, 0.82f},
                {0.96f, 0.45f, 0.26f},
                {0.16f, 0.69f, 0.47f},
                {0.55f, 0.35f, 0.75f},
                {0.85f, 0.65f, 0.13f}
            };

            int seriesIndex = 0;
            for (ChartSeries series : chartData.series()) {
                float[] color = palette[seriesIndex % palette.length];
                seriesIndex++;
                List<Double> values = series.values();
                if (values == null || values.isEmpty()) {
                    continue;
                }
                drawSeriesLine(plotX, plotY, plotW, plotH, timestamps, values, minVal, range, color);
            }

            drawLegend(x + width - 140f, y + 18f, chartData.series(), palette);
        }

        /**
         * 绘制序列折线。
         *
         * @param plotX 绘图区 X
         * @param plotY 绘图区 Y
         * @param plotW 绘图区宽度
         * @param plotH 绘图区高度
         * @param timestamps 时间戳列表
         * @param values 序列值
         * @param minVal 最小值
         * @param range 值域
         * @param color 颜色
         */
        private void drawSeriesLine(float plotX,
                                    float plotY,
                                    float plotW,
                                    float plotH,
                                    List<Long> timestamps,
                                    List<Double> values,
                                    double minVal,
                                    double range,
                                    float[] color) {
            doc.setStrokeColor(color[0], color[1], color[2]);
            doc.setLineWidth(1.1f);

            List<float[]> segment = new ArrayList<>();
            int size = Math.min(timestamps.size(), values.size());
            long start = timestamps.get(0);
            long end = timestamps.get(timestamps.size() - 1);
            double span = Math.max(1d, (double) (end - start));
            for (int i = 0; i < size; i++) {
                Double value = values.get(i);
                if (value == null) {
                    // 遇到空值时断开折线
                    flushSegment(segment);
                    continue;
                }
                double ratioX = (timestamps.get(i) - start) / span;
                double ratioY = (value - minVal) / range;
                float px = (float) (plotX + ratioX * plotW);
                float py = (float) (plotY + plotH - ratioY * plotH);
                segment.add(new float[] { px, py });
            }
            flushSegment(segment);
        }

        /**
         * 刷新当前折线片段。
         *
         * @param segment 折线片段
         */
        private void flushSegment(List<float[]> segment) {
            if (segment.size() < 2) {
                segment.clear();
                return;
            }
            doc.drawPolyline(segment);
            segment.clear();
        }

        /**
         * 绘制图例。
         *
         * @param x 左上角 X
         * @param y 左上角 Y
         * @param series 曲线列表
         * @param palette 调色板
         */
        private void drawLegend(float x, float y, List<ChartSeries> series, float[][] palette) {
            if (series == null || series.isEmpty()) {
                return;
            }
            float itemHeight = 12f;
            float boxSize = 6f;
            float curY = y;
            int index = 0;
            for (ChartSeries item : series) {
                float[] color = palette[index % palette.length];
                doc.setFillColor(color[0], color[1], color[2]);
                doc.drawRect(x, curY, boxSize, boxSize, true, false);
                doc.setFillColor(0.3f, 0.3f, 0.3f);
                doc.drawText(x + boxSize + 6f, curY + 7f, 9, truncate(item.name(), 18), false);
                curY += itemHeight;
                index++;
            }
        }

        /**
         * 绘制分节标题。
         *
         * @param title 标题文本
         */
        private void drawSectionTitle(String title) {
            ensureSpace(28f);
            float width = PAGE_WIDTH - MARGIN_X * 2f;
            doc.setFillColor(0.95f, 0.95f, 0.95f);
            doc.drawRect(MARGIN_X, cursorY, width, 20f, true, false);
            doc.setFillColor(0.22f, 0.25f, 0.32f);
            doc.drawText(MARGIN_X + 8f, cursorY + 14f, 12, title, true);
            cursorY += 26f;
        }

        /**
         * 绘制键值行。
         *
         * @param label 标签
         * @param value 值
         */
        private void drawKeyValue(String label, String value) {
            float labelWidth = 120f;
            float lineHeight = 16f;
            float valueWidth = PAGE_WIDTH - MARGIN_X * 2f - labelWidth - 6f;
            List<String> lines = wrapText(safeText(value, "-"), maxChars(valueWidth, 11));
            float blockHeight = lines.size() * lineHeight;
            ensureSpace(blockHeight + 2f);

            doc.setFillColor(0.25f, 0.25f, 0.25f);
            doc.drawText(MARGIN_X, cursorY + 12f, 11, label, true);
            float textY = cursorY + 12f;
            for (String line : lines) {
                doc.drawText(MARGIN_X + labelWidth, textY, 11, line, false);
                textY += lineHeight;
            }
            cursorY += blockHeight + 2f;
        }

        /**
         * 绘制表头。
         *
         * @param colWidths 列宽
         * @param headers 表头文本
         */
        private void drawTableHeader(float[] colWidths, String[] headers) {
            float headerHeight = 20f;
            ensureSpace(headerHeight + 4f);
            float contentWidth = PAGE_WIDTH - MARGIN_X * 2f;
            doc.setFillColor(0.94f, 0.94f, 0.94f);
            doc.drawRect(MARGIN_X, cursorY, contentWidth, headerHeight, true, false);
            doc.setStrokeColor(0.88f, 0.88f, 0.88f);
            doc.setLineWidth(0.5f);
            doc.drawRect(MARGIN_X, cursorY, contentWidth, headerHeight, false, true);
            float x = MARGIN_X + 4f;
            doc.setFillColor(0.25f, 0.25f, 0.25f);
            for (int i = 0; i < headers.length; i++) {
                doc.drawText(x, cursorY + 14f, 10, headers[i], true);
                x += colWidths[i];
            }
            cursorY += headerHeight;
        }

        /**
         * 绘制说明文本。
         *
         * @param text 说明内容
         */
        private void drawNote(String text) {
            ensureSpace(20f);
            doc.setFillColor(0.4f, 0.4f, 0.4f);
            doc.drawText(MARGIN_X, cursorY + 12f, 10, safeText(text, "-"), false);
            cursorY += 18f;
        }

        /**
         * 确保当前页有足够空间，必要时换页。
         *
         * @param height 预估高度
         */
        private void ensureSpace(float height) {
            if (cursorY + height > PAGE_HEIGHT - MARGIN_BOTTOM) {
                doc.newPage();
                cursorY = MARGIN_TOP;
            }
        }

        /**
         * 估算给定宽度下可容纳的字符数。
         *
         * @param width 宽度
         * @param fontSize 字号
         * @return 字符数
         */
        private int maxChars(float width, int fontSize) {
            return Math.max(1, (int) Math.floor(width / (fontSize * 0.55f)));
        }

        /**
         * 进行文本换行。
         *
         * @param text 文本
         * @param maxChars 单行最大字符数
         * @return 行列表
         */
        private List<String> wrapText(String text, int maxChars) {
            if (text == null || text.isBlank()) {
                return List.of("-");
            }
            if (text.length() <= maxChars) {
                return List.of(text);
            }
            List<String> lines = new ArrayList<>();
            int index = 0;
            while (index < text.length()) {
                int end = Math.min(text.length(), index + maxChars);
                lines.add(text.substring(index, end));
                index = end;
            }
            return lines;
        }

        /**
         * 文本截断并追加省略号。
         *
         * @param text 文本
         * @param maxChars 最大字符数
         * @return 截断后的文本
         */
        private String truncate(String text, int maxChars) {
            if (text == null) {
                return "-";
            }
            if (text.length() <= maxChars) {
                return text;
            }
            return text.substring(0, Math.max(0, maxChars - 3)) + "...";
        }

        /**
         * 处理文本空值与换行。
         *
         * @param text 原始文本
         * @param fallback 兜底文本
         * @return 安全文本
         */
        private String safeText(String text, String fallback) {
            if (text == null || text.isBlank()) {
                return fallback;
            }
            String normalized = text.replace("\r", " ").replace("\n", " ").trim();
            return normalized.isBlank() ? fallback : normalized;
        }

        /**
         * 格式化数值展示。
         *
         * @param value 数值
         * @return 格式化字符串
         */
        private String formatNumber(Double value) {
            if (value == null) {
                return "-";
            }
            return String.format(Locale.ROOT, "%.4f", value);
        }

        /**
         * 格式化时间标签。
         *
         * @param millis 毫秒时间戳
         * @return 时间字符串
         */
        private String formatTimeLabel(long millis) {
            return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_LABEL_FORMATTER);
        }
    }

    private static final class PdfDocument {
        private final float width;
        private final float height;
        private final List<StringBuilder> pages = new ArrayList<>();
        private StringBuilder current;

        private PdfDocument(float width, float height) {
            this.width = width;
            this.height = height;
            newPage();
        }

        /**
         * 新建页面。
         */
        private void newPage() {
            current = new StringBuilder();
            pages.add(current);
        }

        /**
         * 设置描边颜色。
         *
         * @param r 红色分量
         * @param g 绿色分量
         * @param b 蓝色分量
         */
        private void setStrokeColor(float r, float g, float b) {
            current.append(String.format(Locale.ROOT, "%.3f %.3f %.3f RG\n", r, g, b));
        }

        /**
         * 设置填充颜色。
         *
         * @param r 红色分量
         * @param g 绿色分量
         * @param b 蓝色分量
         */
        private void setFillColor(float r, float g, float b) {
            current.append(String.format(Locale.ROOT, "%.3f %.3f %.3f rg\n", r, g, b));
        }

        /**
         * 设置线宽。
         *
         * @param width 线宽
         */
        private void setLineWidth(float width) {
            current.append(String.format(Locale.ROOT, "%.2f w\n", width));
        }

        /**
         * 绘制直线。
         *
         * @param x1 起点 X
         * @param y1Top 起点 Y（顶部坐标）
         * @param x2 终点 X
         * @param y2Top 终点 Y（顶部坐标）
         */
        private void drawLine(float x1, float y1Top, float x2, float y2Top) {
            float y1 = toPdfY(y1Top);
            float y2 = toPdfY(y2Top);
            current.append(String.format(Locale.ROOT, "%.2f %.2f m %.2f %.2f l S\n", x1, y1, x2, y2));
        }

        /**
         * 绘制矩形。
         *
         * @param x 左上角 X
         * @param yTop 左上角 Y（顶部坐标）
         * @param width 宽度
         * @param height 高度
         * @param fill 是否填充
         * @param stroke 是否描边
         */
        private void drawRect(float x, float yTop, float width, float height, boolean fill, boolean stroke) {
            float y = toPdfY(yTop + height);
            current.append(String.format(Locale.ROOT, "%.2f %.2f %.2f %.2f re ", x, y, width, height));
            if (fill && stroke) {
                current.append("B\n");
            } else if (fill) {
                current.append("f\n");
            } else if (stroke) {
                current.append("S\n");
            } else {
                current.append("n\n");
            }
        }

        /**
         * 绘制文本。
         *
         * @param x 左上角 X
         * @param yTop 左上角 Y（顶部坐标）
         * @param fontSize 字号
         * @param text 文本
         * @param bold 是否加粗
         */
        private void drawText(float x, float yTop, int fontSize, String text, boolean bold) {
            if (text == null) {
                return;
            }
            String font = bold ? "F2" : "F1";
            float y = toPdfY(yTop);
            current.append(String.format(Locale.ROOT,
                "BT /%s %d Tf 1 0 0 1 %.2f %.2f Tm (%s) Tj ET\n",
                font,
                fontSize,
                x,
                y,
                escapePdfText(text)));
        }

        /**
         * 绘制折线。
         *
         * @param points 点列表
         */
        private void drawPolyline(List<float[]> points) {
            if (points == null || points.size() < 2) {
                return;
            }
            StringBuilder builder = new StringBuilder();
            float[] first = points.get(0);
            builder.append(String.format(Locale.ROOT, "%.2f %.2f m\n", first[0], toPdfY(first[1])));
            for (int i = 1; i < points.size(); i++) {
                float[] p = points.get(i);
                builder.append(String.format(Locale.ROOT, "%.2f %.2f l\n", p[0], toPdfY(p[1])));
            }
            builder.append("S\n");
            current.append(builder);
        }

        /**
         * 估算文本宽度。
         *
         * @param text 文本
         * @param fontSize 字号
         * @return 估算宽度
         */
        private float estimateTextWidth(String text, float fontSize) {
            if (text == null) {
                return 0f;
            }
            return text.length() * fontSize * 0.55f;
        }

        /**
         * 追加页码。
         */
        private void appendPageNumbers() {
            int total = pages.size();
            for (int i = 0; i < total; i++) {
                StringBuilder page = pages.get(i);
                String text = String.format(Locale.ROOT, "Page %d / %d", i + 1, total);
                float x = width - MARGIN_X - estimateTextWidth(text, 9);
                float yTop = height - MARGIN_BOTTOM + 20f;
                appendTextToPage(page, x, yTop, 9, text, false, 0.5f, 0.5f, 0.5f);
            }
        }

        /**
         * 向指定页面追加文本。
         *
         * @param page 页面缓冲
         * @param x X 坐标
         * @param yTop Y 坐标（顶部坐标）
         * @param fontSize 字号
         * @param text 文本
         * @param bold 是否加粗
         * @param r 红色
         * @param g 绿色
         * @param b 蓝色
         */
        private void appendTextToPage(StringBuilder page,
                                      float x,
                                      float yTop,
                                      int fontSize,
                                      String text,
                                      boolean bold,
                                      float r,
                                      float g,
                                      float b) {
            String font = bold ? "F2" : "F1";
            float y = toPdfY(yTop);
            page.append(String.format(Locale.ROOT, "%.3f %.3f %.3f rg\n", r, g, b));
            page.append(String.format(Locale.ROOT,
                "BT /%s %d Tf 1 0 0 1 %.2f %.2f Tm (%s) Tj ET\n",
                font,
                fontSize,
                x,
                y,
                escapePdfText(text)));
        }

        /**
         * 将顶部坐标转换为 PDF 坐标。
         *
         * @param yTop 顶部坐标
         * @return PDF 坐标
         */
        private float toPdfY(float yTop) {
            return height - yTop;
        }

        /**
         * 构建 PDF 字节数组。
         *
         * @return PDF 字节
         */
        private byte[] build() {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            writeAscii(output, "%PDF-1.4\n");

            offsets.add(output.size());
            writeAscii(output, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            offsets.add(output.size());
            StringBuilder kids = new StringBuilder();
            int pageCount = pages.size();
            int pageStartId = 5;
            for (int i = 0; i < pageCount; i++) {
                if (i > 0) {
                    kids.append(' ');
                }
                kids.append(pageStartId + i).append(" 0 R");
            }
            writeAscii(output, "2 0 obj\n<< /Type /Pages /Kids ["
                + kids + "] /Count " + pageCount + " >>\nendobj\n");

            offsets.add(output.size());
            writeAscii(output, "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
            offsets.add(output.size());
            writeAscii(output, "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n");

            int contentStartId = pageStartId + pageCount;
            for (int i = 0; i < pageCount; i++) {
                int pageId = pageStartId + i;
                int contentId = contentStartId + i;
                offsets.add(output.size());
                writeAscii(output, String.format(Locale.ROOT,
                    "%d 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.0f %.0f] "
                        + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents %d 0 R >>\nendobj\n",
                    pageId,
                    width,
                    height,
                    contentId));
            }

            for (int i = 0; i < pageCount; i++) {
                int contentId = contentStartId + i;
                byte[] contentBytes = pages.get(i).toString().getBytes(StandardCharsets.US_ASCII);
                offsets.add(output.size());
                writeAscii(output, String.format(Locale.ROOT, "%d 0 obj\n<< /Length %d >>\nstream\n",
                    contentId,
                    contentBytes.length));
                try {
                    output.write(contentBytes);
                } catch (Exception ex) {
                    throw new IllegalStateException("写入PDF内容失败: " + ex.getMessage(), ex);
                }
                writeAscii(output, "\nendstream\nendobj\n");
            }

            int xrefPosition = output.size();
            int totalObjects = offsets.size();
            writeAscii(output, "xref\n0 " + totalObjects + "\n");
            writeAscii(output, "0000000000 65535 f \n");
            for (int i = 1; i < offsets.size(); i++) {
                writeAscii(output, String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
            }
            writeAscii(output, "trailer\n<< /Size " + totalObjects + " /Root 1 0 R >>\nstartxref\n");
            writeAscii(output, String.valueOf(xrefPosition));
            writeAscii(output, "\n%%EOF\n");
            return output.toByteArray();
        }

        /**
         * 写入 ASCII 文本。
         *
         * @param output 输出流
         * @param text 文本
         */
        private void writeAscii(ByteArrayOutputStream output, String text) {
            try {
                output.write(text.getBytes(StandardCharsets.US_ASCII));
            } catch (Exception ex) {
                throw new IllegalStateException("写入PDF失败: " + ex.getMessage(), ex);
            }
        }

        /**
         * 转义 PDF 文本中的特殊字符。
         *
         * @param text 文本
         * @return 转义后的文本
         */
        private String escapePdfText(String text) {
            if (text == null) {
                return "";
            }
            return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }
    }
}
