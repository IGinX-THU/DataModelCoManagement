package com.xmu.iginx.assoc.modules.analysis.util;

import com.xmu.iginx.assoc.common.exception.ExceptionMessageUtils;
import lombok.Data;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 报告 PDF 构建器，负责将报告内容渲染为 PDF 字节。
 */
public class ReportPdfBuilder {

    private static final float PAGE_WIDTH = 595f;
    private static final float PAGE_HEIGHT = 842f;
    private static final float MARGIN_X = 34f;
    private static final float MARGIN_TOP = 34f;
    private static final float MARGIN_BOTTOM = 34f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN_X * 2f;
    private static final float CARD_GAP = 12f;
    private static final DateTimeFormatter TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * 报告内容模型。
     */
    @Data
    public static class ReportContent {
        private String title = "IGinX 智能关联分析报告";
        private String subtitle = "-";
        private String analysisMode = "TIME_SERIES";
        private String generatedAt = "-";
        private String taskId = "-";
        private String taskName = "-";
        private String ruleId = "-";
        private String ruleName = "-";
        private String modelName = "-";
        private String modelVersion = "-";
        private String modelType = "-";
        private String functionName = "-";
        private String executor = "-";
        private String createTime = "-";
        private String rangeStart = "-";
        private String rangeEnd = "-";
        private String startTime = "-";
        private String endTime = "-";
        private String defaultResultPrefix = "-";
        private List<BindingItem> inputBindings = List.of();
        private List<BindingItem> outputBindings = List.of();
        private List<String> guideLines = List.of();
        private List<MetricRow> metrics = List.of();
        private List<TableBlock> tableBlocks = List.of();
        private ChartData chartData;
        private List<StructuredChartBlock> structuredChartBlocks = List.of();
        private boolean includeStats = true;
        private boolean includeCharts = true;
    }

    /**
     * 统计摘要行。
     */
    public record MetricRow(String label, long count, Double min, Double max, Double avg) {
    }

    /**
     * 路径绑定项。
     */
    public record BindingItem(String name, String path, String pathKind) {
    }

    /**
     * 图表序列数据。
     */
    public record ChartSeries(String name, List<Double> values) {
    }

    /**
     * 时序图表数据。
     */
    public record ChartData(List<Long> timestamps, List<ChartSeries> series, Double min, Double max) {
    }

    /**
     * 结构化表格预览块。
     */
    public record TableBlock(String title, String note, List<String> columns, List<Map<String, Object>> rows) {
    }

    /**
     * 结构化散点。
     */
    public record StructuredChartPoint(Double x, Double y, String label) {
    }

    /**
     * 结构化图表块。
     */
    public record StructuredChartBlock(String title,
                                       String description,
                                       String chartType,
                                       String xAxisLabel,
                                       String yAxisLabel,
                                       List<String> categories,
                                       List<Double> values,
                                       List<StructuredChartPoint> points) {
    }

    /**
     * 构建 PDF 字节。
     */
    public byte[] build(ReportContent content) {
        PdfDocument doc = new PdfDocument(PAGE_WIDTH, PAGE_HEIGHT);
        Layout layout = new Layout(doc);
        layout.render(content == null ? new ReportContent() : content);
        doc.appendPageNumbers();
        return doc.build();
    }

    @FunctionalInterface
    private interface ChartDrawer {
        void draw(float x, float y, float width, float height);
    }

    private record PlotArea(float x, float y, float width, float height) {
    }

    private record DoubleRange(double min, double max) {
        private double span() {
            return Math.max(1d, max - min);
        }
    }

    private static final class Layout {
        private final PdfDocument doc;
        private float cursorY = MARGIN_TOP;

        private Layout(PdfDocument doc) {
            this.doc = doc;
        }

        /**
         * 渲染完整报告内容。
         */
        private void render(ReportContent content) {
            drawHero(content);
            drawSummaryCards(content);
            drawOverview(content);
            drawBindingTable("输入绑定与数据集定位", content.getInputBindings());
            drawBindingTable("输出绑定与结果定位", content.getOutputBindings());
            if (content.isIncludeStats()) {
                drawMetrics(content.getMetrics());
            }
            drawTableBlocks(content.getTableBlocks());
            if (content.isIncludeCharts()) {
                drawCharts(content);
            }
            drawGuide(content.getGuideLines());
        }

        /**
         * 绘制头图区域。
         */
        private void drawHero(ReportContent content) {
            List<String> subtitleLines = wrapTextByWidth(safeText(content.getSubtitle(), "-"), CONTENT_WIDTH - 150f, 11);
            float heroHeight = 82f + Math.max(0, subtitleLines.size() - 1) * 14f;
            ensureSpace(heroHeight + 24f);
            doc.setFillColor(0.09f, 0.28f, 0.53f);
            doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, heroHeight, true, false);

            doc.setFillColor(1f, 1f, 1f);
            doc.drawText(MARGIN_X + 18f, cursorY + 28f, 20, safeText(content.getTitle(), "IGinX 智能关联分析报告"), true);
            float subtitleY = cursorY + 50f;
            for (String line : subtitleLines) {
                doc.drawText(MARGIN_X + 18f, subtitleY, 11, line, false);
                subtitleY += 14f;
            }

            String generatedAt = "生成时间: " + safeText(content.getGeneratedAt(), "-");
            float textWidth = doc.estimateTextWidth(generatedAt, 10f);
            doc.drawText(PAGE_WIDTH - MARGIN_X - textWidth - 18f, cursorY + 28f, 10, generatedAt, false);

            doc.setFillColor(0.91f, 0.96f, 1f);
            float modeTop = cursorY + heroHeight - 28f;
            doc.drawRect(MARGIN_X + 18f, modeTop, 130f, 18f, true, false);
            doc.setFillColor(0.05f, 0.22f, 0.40f);
            doc.drawText(MARGIN_X + 26f, modeTop + 13f, 10, "分析模式: " + formatModeLabel(content.getAnalysisMode()), true);
            cursorY += heroHeight + 16f;
        }

        /**
         * 绘制摘要卡片。
         */
        private void drawSummaryCards(ReportContent content) {
            drawSectionTitle("摘要卡片");
            float gap = CARD_GAP;
            float cardWidth = (CONTENT_WIDTH - gap) / 2f;
            float cardHeight = 62f;
            float rowStart = cursorY;

            drawInfoCard(MARGIN_X, rowStart, cardWidth, cardHeight, "任务编号", content.getTaskId(), 0.92f, 0.96f, 1f);
            drawInfoCard(MARGIN_X + cardWidth + gap, rowStart, cardWidth, cardHeight, "规则", content.getRuleName(), 0.94f, 0.98f, 0.94f);
            rowStart += cardHeight + gap;
            drawInfoCard(MARGIN_X, rowStart, cardWidth, cardHeight, "模型", content.getModelName() + " / " + content.getModelVersion(), 0.98f, 0.95f, 0.90f);
            drawInfoCard(MARGIN_X + cardWidth + gap, rowStart, cardWidth, cardHeight, "任务名称", content.getTaskName(), 0.95f, 0.94f, 0.98f);
            cursorY = rowStart + cardHeight + 8f;
        }

        /**
         * 绘制信息卡片。
         */
        private void drawInfoCard(float x,
                                  float y,
                                  float width,
                                  float height,
                                  String title,
                                  String value,
                                  float r,
                                  float g,
                                  float b) {
            ensureSpace(height + 4f);
            doc.setFillColor(r, g, b);
            doc.drawRect(x, y, width, height, true, false);
            doc.setStrokeColor(0.86f, 0.90f, 0.95f);
            doc.setLineWidth(0.6f);
            doc.drawRect(x, y, width, height, false, true);

            doc.setFillColor(0.26f, 0.33f, 0.41f);
            doc.drawText(x + 10f, y + 16f, 9, title, true);
            doc.setFillColor(0.12f, 0.18f, 0.24f);
            List<String> lines = wrapTextByWidth(safeText(value, "-"), width - 20f, 11);
            float textY = y + 34f;
            int maxLines = Math.min(2, lines.size());
            for (int index = 0; index < maxLines; index++) {
                String line = index == 1 && lines.size() > 2
                    ? truncateByWidth(lines.get(index), width - 20f, 11)
                    : lines.get(index);
                doc.drawText(x + 10f, textY, 11, line, false);
                textY += 14f;
            }
        }

        /**
         * 绘制任务概览。
         */
        private void drawOverview(ReportContent content) {
            drawSectionTitle("任务上下文");
            drawKeyValue("任务名称", content.getTaskName());
            drawKeyValue("任务编号", content.getTaskId());
            drawKeyValue("规则信息", safeText(content.getRuleName(), "-") + " / " + safeText(content.getRuleId(), "-"));
            drawKeyValue("模型信息", safeText(content.getModelName(), "-") + " / " + safeText(content.getModelVersion(), "-"));
            drawKeyValue("模型类型", content.getModelType());
            drawKeyValue("调用函数", content.getFunctionName());
            drawKeyValue("创建时间", content.getCreateTime());
            drawKeyValue("执行开始", content.getStartTime());
            drawKeyValue("执行结束", content.getEndTime());
            drawKeyValue("数据范围开始", content.getRangeStart());
            drawKeyValue("数据范围结束", content.getRangeEnd());
            drawKeyValue("默认结果前缀", content.getDefaultResultPrefix());
        }

        /**
         * 绘制绑定表。
         */
        private void drawBindingTable(String title, List<BindingItem> bindings) {
            drawSectionTitle(title);
            if (bindings == null || bindings.isEmpty()) {
                drawNote("当前无可用绑定信息。");
                return;
            }
            float[] widths = new float[] { 90f, CONTENT_WIDTH - 90f - 68f, 68f };
            drawTableHeader(widths, new String[] { "参数", "IGinX 路径", "类型" });
            int rowIndex = 0;
            for (BindingItem item : bindings) {
                List<String> nameLines = wrapTextByWidth(safeText(item.name(), "-"), widths[0] - 10f, 9);
                List<String> pathLines = wrapTextByWidth(safeText(item.path(), "-"), widths[1] - 10f, 9);
                List<String> kindLines = wrapTextByWidth(safeText(item.pathKind(), "-"), widths[2] - 10f, 9);
                int lineCount = Math.max(nameLines.size(), Math.max(pathLines.size(), kindLines.size()));
                float rowHeight = Math.max(22f, lineCount * 12f + 8f);
                if (cursorY + rowHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    doc.newPage();
                    cursorY = MARGIN_TOP;
                    drawSectionTitle(title + "（续）");
                    drawTableHeader(widths, new String[] { "参数", "IGinX 路径", "类型" });
                }
                if (rowIndex % 2 == 1) {
                    doc.setFillColor(0.98f, 0.99f, 1f);
                    doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, rowHeight, true, false);
                }
                doc.setStrokeColor(0.90f, 0.92f, 0.95f);
                doc.setLineWidth(0.4f);
                doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, rowHeight, false, true);

                float textY = cursorY + 12f;
                doc.setFillColor(0.17f, 0.22f, 0.28f);
                drawWrappedCell(MARGIN_X + 6f, textY, 9, nameLines, 12f);
                drawWrappedCell(MARGIN_X + widths[0] + 6f, textY, 9, pathLines, 12f);
                drawWrappedCell(MARGIN_X + widths[0] + widths[1] + 6f, textY, 9, kindLines, 12f);
                cursorY += rowHeight;
                rowIndex++;
            }
            cursorY += 8f;
        }

        /**
         * 绘制统计摘要。
         */
        private void drawMetrics(List<MetricRow> metrics) {
            drawSectionTitle("统计摘要");
            if (metrics == null || metrics.isEmpty()) {
                drawNote("当前无数值统计结果。");
                return;
            }
            float[] widths = new float[] { CONTENT_WIDTH - 58f * 4f, 58f, 58f, 58f, 58f };
            drawTableHeader(widths, new String[] { "标签", "数量", "最小", "最大", "均值" });
            int rowIndex = 0;
            for (MetricRow row : metrics) {
                List<String> labelLines = wrapTextByWidth(safeText(row.label(), "-"), widths[0] - 10f, 9);
                float rowHeight = Math.max(22f, labelLines.size() * 12f + 8f);
                if (cursorY + rowHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    doc.newPage();
                    cursorY = MARGIN_TOP;
                    drawSectionTitle("统计摘要（续）");
                    drawTableHeader(widths, new String[] { "标签", "数量", "最小", "最大", "均值" });
                }
                if (rowIndex % 2 == 1) {
                    doc.setFillColor(0.98f, 0.99f, 1f);
                    doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, rowHeight, true, false);
                }
                doc.setStrokeColor(0.90f, 0.92f, 0.95f);
                doc.setLineWidth(0.4f);
                doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, rowHeight, false, true);

                float textY = cursorY + 12f;
                doc.setFillColor(0.17f, 0.22f, 0.28f);
                drawWrappedCell(MARGIN_X + 6f, textY, 9, labelLines, 12f);
                doc.drawText(MARGIN_X + widths[0] + 6f, textY, 9, String.valueOf(row.count()), false);
                doc.drawText(MARGIN_X + widths[0] + widths[1] + 6f, textY, 9, formatNumber(row.min()), false);
                doc.drawText(MARGIN_X + widths[0] + widths[1] * 2f + 6f, textY, 9, formatNumber(row.max()), false);
                doc.drawText(MARGIN_X + widths[0] + widths[1] * 3f + 6f, textY, 9, formatNumber(row.avg()), false);
                cursorY += rowHeight;
                rowIndex++;
            }
            cursorY += 8f;
        }

        /**
         * 绘制表格预览块。
         */
        private void drawTableBlocks(List<TableBlock> tableBlocks) {
            if (tableBlocks == null || tableBlocks.isEmpty()) {
                return;
            }
            for (TableBlock block : tableBlocks) {
                drawSectionTitle(block.title());
                if (hasText(block.note())) {
                    drawNote(block.note());
                }
                drawDataTable(block.columns(), block.rows());
            }
        }

        /**
         * 绘制数据表。
         */
        private void drawDataTable(List<String> columns, List<Map<String, Object>> rows) {
            if (columns == null || columns.isEmpty() || rows == null || rows.isEmpty()) {
                drawNote("当前无预览数据。");
                return;
            }
            int columnCount = Math.max(1, columns.size());
            float[] widths = new float[columnCount];
            float width = CONTENT_WIDTH / columnCount;
            for (int index = 0; index < columnCount; index++) {
                widths[index] = width;
            }
            drawTableHeader(widths, columns.toArray(String[]::new));
            int rowIndex = 0;
            for (Map<String, Object> row : rows) {
                List<List<String>> lineGroups = new ArrayList<>();
                int maxLines = 1;
                for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                    String column = columns.get(colIndex);
                    String value = row == null ? "-" : safeText(String.valueOf(row.getOrDefault(column, "-")), "-");
                    List<String> lines = wrapTextByWidth(value, widths[colIndex] - 10f, 9);
                    lineGroups.add(lines);
                    maxLines = Math.max(maxLines, lines.size());
                }
                float rowHeight = Math.max(22f, maxLines * 12f + 8f);
                if (cursorY + rowHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    doc.newPage();
                    cursorY = MARGIN_TOP;
                    drawSectionTitle("数据预览（续）");
                    drawTableHeader(widths, columns.toArray(String[]::new));
                }
                if (rowIndex % 2 == 1) {
                    doc.setFillColor(0.98f, 0.99f, 1f);
                    doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, rowHeight, true, false);
                }
                doc.setStrokeColor(0.90f, 0.92f, 0.95f);
                doc.setLineWidth(0.4f);
                doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, rowHeight, false, true);

                float x = MARGIN_X + 6f;
                float textY = cursorY + 12f;
                doc.setFillColor(0.17f, 0.22f, 0.28f);
                for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                    drawWrappedCell(x, textY, 9, lineGroups.get(colIndex), 12f);
                    x += widths[colIndex];
                }
                cursorY += rowHeight;
                rowIndex++;
            }
            cursorY += 8f;
        }

        /**
         * 绘制图表区域。
         */
        private void drawCharts(ReportContent content) {
            if (content.getChartData() != null) {
                drawSectionTitle("时序趋势图");
                drawChartCard("输出结果折线图", "图表会根据 PDF 容量自动下采样，以兼顾可读性与渲染性能。", 248f,
                    (x, y, width, height) -> drawTimeSeriesChart(x, y, width, height, content.getChartData()));
            }
            if (content.getStructuredChartBlocks() != null && !content.getStructuredChartBlocks().isEmpty()) {
                drawSectionTitle("结构化分析图");
                for (StructuredChartBlock block : content.getStructuredChartBlocks()) {
                    drawChartCard(block.title(), block.description(), 228f,
                        (x, y, width, height) -> drawStructuredChart(x, y, width, height, block));
                }
            }
        }

        /**
         * 绘制定位指引。
         */
        private void drawGuide(List<String> lines) {
            drawSectionTitle("复核与定位指引");
            if (lines == null || lines.isEmpty()) {
                drawNote("当前无额外定位指引。");
                return;
            }
            List<List<String>> wrappedLines = new ArrayList<>();
            int totalLineCount = 0;
            for (String line : lines) {
                List<String> lineGroup = wrapTextByWidth(safeText(line, "-"), CONTENT_WIDTH - 50f, 10);
                wrappedLines.add(lineGroup);
                totalLineCount += lineGroup.size();
            }
            float boxHeight = 16f + totalLineCount * 14f + 8f;
            ensureSpace(boxHeight + 6f);
            doc.setFillColor(0.95f, 0.98f, 1f);
            doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, boxHeight, true, false);
            doc.setStrokeColor(0.82f, 0.89f, 0.97f);
            doc.setLineWidth(0.6f);
            doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, boxHeight, false, true);

            float y = cursorY + 16f;
            for (List<String> lineGroup : wrappedLines) {
                doc.setFillColor(0.10f, 0.24f, 0.38f);
                doc.drawText(MARGIN_X + 10f, y, 9, "-", false);
                drawWrappedCell(MARGIN_X + 22f, y, 10, lineGroup, 14f);
                y += lineGroup.size() * 14f;
            }
            cursorY += boxHeight + 8f;
        }

        /**
         * 绘制图表卡片。
         */
        private void drawChartCard(String title, String description, float chartHeight, ChartDrawer drawer) {
            List<String> descriptionLines = hasText(description)
                ? wrapTextByWidth(description, CONTENT_WIDTH - 24f, 9)
                : List.of();
            float headerHeight = 26f + descriptionLines.size() * 12f;
            ensureSpace(chartHeight + headerHeight + 22f);
            doc.setFillColor(0.99f, 0.99f, 1f);
            doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, chartHeight + headerHeight, true, false);
            doc.setStrokeColor(0.88f, 0.91f, 0.95f);
            doc.setLineWidth(0.6f);
            doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, chartHeight + headerHeight, false, true);

            doc.setFillColor(0.12f, 0.19f, 0.28f);
            doc.drawText(MARGIN_X + 12f, cursorY + 18f, 12, safeText(title, "图表"), true);
            if (!descriptionLines.isEmpty()) {
                doc.setFillColor(0.38f, 0.46f, 0.54f);
                drawWrappedCell(MARGIN_X + 12f, cursorY + 32f, 9, descriptionLines, 12f);
            }

            float chartX = MARGIN_X + 12f;
            float chartY = cursorY + headerHeight - 4f;
            float chartW = CONTENT_WIDTH - 24f;
            drawer.draw(chartX, chartY, chartW, chartHeight);
            cursorY += chartHeight + headerHeight + 12f;
        }

        /**
         * 绘制时序图。
         */
        private void drawTimeSeriesChart(float x, float y, float width, float height, ChartData chartData) {
            if (chartData == null || chartData.timestamps() == null || chartData.timestamps().isEmpty()
                || chartData.series() == null || chartData.series().isEmpty()) {
                drawNote("当前无时序图数据。");
                return;
            }
            PlotArea plot = createPlotArea(x, y, width, height, 46f, 20f, 20f, 32f);
            drawPlotFrame(plot);

            double minValue = chartData.min() == null ? 0d : chartData.min();
            double maxValue = chartData.max() == null ? 1d : chartData.max();
            if (Double.compare(minValue, maxValue) == 0) {
                minValue -= 0.5d;
                maxValue += 0.5d;
            }
            double range = maxValue - minValue;

            List<Long> timestamps = chartData.timestamps();
            long start = timestamps.get(0);
            long end = timestamps.get(timestamps.size() - 1);
            double span = Math.max(1d, end - start);

            float[][] palette = new float[][] {
                {0.17f, 0.39f, 0.80f},
                {0.89f, 0.36f, 0.18f},
                {0.11f, 0.62f, 0.43f},
                {0.55f, 0.32f, 0.73f},
                {0.80f, 0.66f, 0.12f}
            };
            int seriesIndex = 0;
            for (ChartSeries series : chartData.series()) {
                List<float[]> segment = new ArrayList<>();
                List<Double> values = series.values() == null ? List.of() : series.values();
                for (int index = 0; index < Math.min(values.size(), timestamps.size()); index++) {
                    Double value = values.get(index);
                    if (value == null) {
                        flushPolyline(segment, palette[seriesIndex % palette.length], false);
                        continue;
                    }
                    float px = (float) (plot.x() + ((timestamps.get(index) - start) / span) * plot.width());
                    float py = (float) (plot.y() + plot.height() - ((value - minValue) / range) * plot.height());
                    segment.add(new float[] { px, py });
                }
                flushPolyline(segment, palette[seriesIndex % palette.length], true);
                seriesIndex++;
            }

            drawValueLabels(plot, minValue, maxValue);
            drawTimeLabels(plot, start, end);
            drawLegend(plot.x() + plot.width() - 120f, plot.y() + 10f, chartData.series(), palette);
        }

        /**
         * 绘制结构化图表。
         */
        private void drawStructuredChart(float x, float y, float width, float height, StructuredChartBlock block) {
            if (block == null) {
                return;
            }
            if ("SCATTER".equalsIgnoreCase(block.chartType())) {
                drawScatterChart(x, y, width, height, block);
                return;
            }
            if ("HISTOGRAM".equalsIgnoreCase(block.chartType())) {
                drawHistogramChart(x, y, width, height, block);
                return;
            }
            drawCategoryLineChart(x, y, width, height, block);
        }

        /**
         * 绘制结构化折线图。
         */
        private void drawCategoryLineChart(float x, float y, float width, float height, StructuredChartBlock block) {
            if (block.values() == null || block.values().isEmpty()) {
                drawNote("当前无曲线图数据。");
                return;
            }
            PlotArea plot = createPlotArea(x, y, width, height, 42f, 20f, 20f, 34f);
            drawPlotFrame(plot);
            DoubleRange range = resolveRange(block.values());
            List<float[]> points = new ArrayList<>();
            int total = block.values().size();
            for (int index = 0; index < total; index++) {
                Double value = block.values().get(index);
                if (value == null) {
                    continue;
                }
                float px = total == 1
                    ? plot.x() + plot.width() / 2f
                    : plot.x() + (plot.width() * index / (total - 1f));
                float py = (float) (plot.y() + plot.height() - ((value - range.min()) / range.span()) * plot.height());
                points.add(new float[] { px, py });
            }
            doc.setStrokeColor(0.17f, 0.39f, 0.80f);
            doc.setLineWidth(1.2f);
            doc.drawPolyline(points);
            for (float[] point : points) {
                doc.setFillColor(0.17f, 0.39f, 0.80f);
                doc.drawRect(point[0] - 1.6f, point[1] - 1.6f, 3.2f, 3.2f, true, false);
            }

            drawValueLabels(plot, range.min(), range.max());
            drawCategoryLabels(plot, block.categories());
            drawAxisNames(plot, block.xAxisLabel(), block.yAxisLabel());
        }

        /**
         * 绘制结构化直方图。
         */
        private void drawHistogramChart(float x, float y, float width, float height, StructuredChartBlock block) {
            if (block.values() == null || block.values().isEmpty()) {
                drawNote("当前无直方图数据。");
                return;
            }
            PlotArea plot = createPlotArea(x, y, width, height, 42f, 20f, 20f, 34f);
            drawPlotFrame(plot);
            DoubleRange range = resolveRange(block.values());
            int total = block.values().size();
            float barGap = 4f;
            float barWidth = Math.max(8f, (plot.width() - Math.max(0, total - 1) * barGap) / total);
            for (int index = 0; index < total; index++) {
                Double value = block.values().get(index);
                if (value == null) {
                    continue;
                }
                float ratio = (float) ((value - range.min()) / range.span());
                float barHeight = plot.height() * ratio;
                float barX = plot.x() + index * (barWidth + barGap);
                float barY = plot.y() + plot.height() - barHeight;
                doc.setFillColor(0.89f, 0.36f, 0.18f);
                doc.drawRect(barX, barY, barWidth, barHeight, true, false);
            }
            drawValueLabels(plot, range.min(), range.max());
            drawCategoryLabels(plot, block.categories());
            drawAxisNames(plot, block.xAxisLabel(), block.yAxisLabel());
        }

        /**
         * 绘制结构化散点图。
         */
        private void drawScatterChart(float x, float y, float width, float height, StructuredChartBlock block) {
            if (block.points() == null || block.points().isEmpty()) {
                drawNote("当前无散点图数据。");
                return;
            }
            PlotArea plot = createPlotArea(x, y, width, height, 42f, 20f, 20f, 34f);
            drawPlotFrame(plot);

            List<Double> xValues = new ArrayList<>();
            List<Double> yValues = new ArrayList<>();
            for (StructuredChartPoint point : block.points()) {
                if (point != null && point.x() != null && point.y() != null) {
                    xValues.add(point.x());
                    yValues.add(point.y());
                }
            }
            DoubleRange xRange = resolveRange(xValues);
            DoubleRange yRange = resolveRange(yValues);
            for (StructuredChartPoint point : block.points()) {
                if (point == null || point.x() == null || point.y() == null) {
                    continue;
                }
                float px = (float) (plot.x() + ((point.x() - xRange.min()) / xRange.span()) * plot.width());
                float py = (float) (plot.y() + plot.height() - ((point.y() - yRange.min()) / yRange.span()) * plot.height());
                doc.setFillColor(0.11f, 0.56f, 0.52f);
                doc.drawRect(px - 2f, py - 2f, 4f, 4f, true, false);
            }

            drawValueLabels(plot, yRange.min(), yRange.max());
            drawBottomLabels(plot, formatNumber(xRange.min()), formatNumber(xRange.max()));
            drawAxisNames(plot, block.xAxisLabel(), block.yAxisLabel());
        }

        /**
         * 创建绘图区。
         */
        private PlotArea createPlotArea(float x,
                                        float y,
                                        float width,
                                        float height,
                                        float paddingLeft,
                                        float paddingRight,
                                        float paddingTop,
                                        float paddingBottom) {
            return new PlotArea(
                x + paddingLeft,
                y + paddingTop,
                width - paddingLeft - paddingRight,
                height - paddingTop - paddingBottom
            );
        }

        /**
         * 绘制绘图区边框和网格。
         */
        private void drawPlotFrame(PlotArea plot) {
            doc.setStrokeColor(0.86f, 0.89f, 0.93f);
            doc.setLineWidth(0.6f);
            doc.drawRect(plot.x(), plot.y(), plot.width(), plot.height(), false, true);
            for (int index = 1; index < 4; index++) {
                float y = plot.y() + plot.height() * index / 4f;
                float x = plot.x() + plot.width() * index / 4f;
                doc.setStrokeColor(0.93f, 0.95f, 0.97f);
                doc.setLineWidth(0.4f);
                doc.drawLine(plot.x(), y, plot.x() + plot.width(), y);
                doc.drawLine(x, plot.y(), x, plot.y() + plot.height());
            }
        }

        /**
         * 绘制数值轴标签。
         */
        private void drawValueLabels(PlotArea plot, double minValue, double maxValue) {
            doc.setFillColor(0.43f, 0.49f, 0.56f);
            doc.drawText(plot.x() - 34f, plot.y() + 8f, 8, formatNumber(maxValue), false);
            doc.drawText(plot.x() - 34f, plot.y() + plot.height() + 8f, 8, formatNumber(minValue), false);
        }

        /**
         * 绘制时间标签。
         */
        private void drawTimeLabels(PlotArea plot, long start, long end) {
            drawBottomLabels(plot, formatTimeLabel(start), formatTimeLabel(end));
        }

        /**
         * 绘制类目标签。
         */
        private void drawCategoryLabels(PlotArea plot, List<String> categories) {
            if (categories == null || categories.isEmpty()) {
                return;
            }
            int middle = Math.max(0, categories.size() / 2);
            drawBottomLabels(
                plot,
                safeLabel(categories.get(0), 18),
                safeLabel(categories.get(middle), 18),
                safeLabel(categories.get(categories.size() - 1), 18)
            );
        }

        /**
         * 绘制底部标签。
         */
        private void drawBottomLabels(PlotArea plot, String startLabel, String endLabel) {
            drawBottomLabels(plot, startLabel, "", endLabel);
        }

        /**
         * 绘制底部标签。
         */
        private void drawBottomLabels(PlotArea plot, String startLabel, String middleLabel, String endLabel) {
            doc.setFillColor(0.43f, 0.49f, 0.56f);
            doc.drawText(plot.x(), plot.y() + plot.height() + 18f, 8, safeText(startLabel, "-"), false);
            if (hasText(middleLabel)) {
                float middleX = plot.x() + plot.width() / 2f - doc.estimateTextWidth(middleLabel, 8f) / 2f;
                doc.drawText(middleX, plot.y() + plot.height() + 18f, 8, safeText(middleLabel, "-"), false);
            }
            float endWidth = doc.estimateTextWidth(safeText(endLabel, "-"), 8f);
            doc.drawText(plot.x() + plot.width() - endWidth, plot.y() + plot.height() + 18f, 8, safeText(endLabel, "-"), false);
        }

        /**
         * 绘制坐标轴名称。
         */
        private void drawAxisNames(PlotArea plot, String xAxisName, String yAxisName) {
            if (hasText(yAxisName)) {
                doc.setFillColor(0.31f, 0.36f, 0.43f);
                doc.drawText(plot.x() - 34f, plot.y() - 4f, 8, safeLabel(yAxisName, 12), false);
            }
            if (hasText(xAxisName)) {
                doc.setFillColor(0.31f, 0.36f, 0.43f);
                float width = doc.estimateTextWidth(xAxisName, 8f);
                doc.drawText(plot.x() + plot.width() - width, plot.y() + plot.height() + 30f, 8, safeLabel(xAxisName, 16), false);
            }
        }

        /**
         * 绘制图例。
         */
        private void drawLegend(float x, float y, List<ChartSeries> series, float[][] palette) {
            if (series == null || series.isEmpty()) {
                return;
            }
            float curY = y;
            int index = 0;
            for (ChartSeries item : series) {
                float[] color = palette[index % palette.length];
                doc.setFillColor(color[0], color[1], color[2]);
                doc.drawRect(x, curY, 7f, 7f, true, false);
                doc.setFillColor(0.26f, 0.32f, 0.39f);
                doc.drawText(x + 10f, curY + 7f, 8, truncateByWidth(safeText(item.name(), "-"), 100f, 8), false);
                curY += 12f;
                index++;
            }
        }

        /**
         * 绘制分节标题。
         */
        private void drawSectionTitle(String title) {
            ensureSpace(28f);
            doc.setFillColor(0.95f, 0.97f, 1f);
            doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, 20f, true, false);
            doc.setFillColor(0.12f, 0.20f, 0.31f);
            doc.drawText(MARGIN_X + 8f, cursorY + 14f, 12, safeText(title, "章节"), true);
            cursorY += 28f;
        }

        /**
         * 绘制键值行。
         */
        private void drawKeyValue(String label, String value) {
            float labelWidth = 96f;
            float valueWidth = CONTENT_WIDTH - labelWidth - 6f;
            List<String> lines = wrapTextByWidth(safeText(value, "-"), valueWidth, 10);
            float lineHeight = 16f;
            float blockHeight = Math.max(16f, lines.size() * lineHeight);
            ensureSpace(blockHeight + 2f);

            doc.setFillColor(0.18f, 0.24f, 0.31f);
            doc.drawText(MARGIN_X, cursorY + 12f, 10, safeText(label, "-"), true);
            float textY = cursorY + 12f;
            for (String line : lines) {
                doc.drawText(MARGIN_X + labelWidth, textY, 10, line, false);
                textY += lineHeight;
            }
            cursorY += blockHeight + 2f;
        }

        /**
         * 绘制表头。
         */
        private void drawTableHeader(float[] colWidths, String[] headers) {
            float headerHeight = 22f;
            ensureSpace(headerHeight + 4f);
            doc.setFillColor(0.94f, 0.96f, 0.99f);
            doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, headerHeight, true, false);
            doc.setStrokeColor(0.87f, 0.90f, 0.94f);
            doc.setLineWidth(0.5f);
            doc.drawRect(MARGIN_X, cursorY, CONTENT_WIDTH, headerHeight, false, true);
            float x = MARGIN_X + 6f;
            doc.setFillColor(0.22f, 0.28f, 0.35f);
            for (int i = 0; i < headers.length; i++) {
                doc.drawText(x, cursorY + 15f, 9, truncateByWidth(safeText(headers[i], "-"), colWidths[i] - 10f, 9), true);
                x += colWidths[i];
            }
            cursorY += headerHeight;
        }

        /**
         * 绘制说明文本。
         */
        private void drawNote(String text) {
            ensureSpace(18f);
            doc.setFillColor(0.42f, 0.48f, 0.55f);
            doc.drawText(MARGIN_X, cursorY + 12f, 9, safeText(text, "-"), false);
            cursorY += 18f;
        }

        /**
         * 确保页面空间足够。
         */
        private void ensureSpace(float height) {
            if (cursorY + height > PAGE_HEIGHT - MARGIN_BOTTOM) {
                doc.newPage();
                cursorY = MARGIN_TOP;
            }
        }

        /**
         * 文本换行。
         */
        private List<String> wrapTextByWidth(String text, float width, int fontSize) {
            List<String> lines = new ArrayList<>();
            if (!hasText(text)) {
                lines.add("-");
                return lines;
            }
            StringBuilder builder = new StringBuilder();
            float currentWidth = 0f;
            for (int index = 0; index < text.length(); index++) {
                char ch = text.charAt(index);
                float charWidth = estimateCharWidth(ch, fontSize);
                if (currentWidth + charWidth > width && builder.length() > 0) {
                    lines.add(builder.toString());
                    builder.setLength(0);
                    currentWidth = 0f;
                }
                builder.append(ch);
                currentWidth += charWidth;
            }
            if (builder.length() > 0) {
                lines.add(builder.toString());
            }
            return lines.isEmpty() ? List.of("-") : lines;
        }

        /**
         * 按宽度截断文本。
         */
        private String truncateByWidth(String text, float width, int fontSize) {
            if (!hasText(text)) {
                return "-";
            }
            if (doc.estimateTextWidth(text, fontSize) <= width) {
                return text;
            }
            String ellipsis = "...";
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < text.length(); index++) {
                char ch = text.charAt(index);
                if (doc.estimateTextWidth(builder.toString() + ch + ellipsis, fontSize) > width) {
                    break;
                }
                builder.append(ch);
            }
            return builder.isEmpty() ? ellipsis : builder + ellipsis;
        }

        /**
         * 安全文本。
         */
        private String safeText(String text, String fallback) {
            if (text == null || text.isBlank()) {
                return fallback;
            }
            String normalized = text.replace("\r", " ").replace("\n", " ").trim();
            return normalized.isBlank() ? fallback : normalized;
        }

        /**
         * 判断文本是否存在。
         */
        private boolean hasText(String text) {
            return text != null && !text.isBlank();
        }

        /**
         * 估算字符宽度。
         */
        private float estimateCharWidth(char ch, int fontSize) {
            if (Character.isWhitespace(ch)) {
                return fontSize * 0.28f;
            }
            if (ch < 128) {
                return fontSize * 0.56f;
            }
            return fontSize * 0.96f;
        }

        /**
         * 安全标签。
         */
        private String safeLabel(String text, int maxLength) {
            String safe = safeText(text, "-");
            if (safe.length() <= maxLength) {
                return safe;
            }
            return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
        }

        /**
         * 绘制自动换行的单元格文本。
         */
        private void drawWrappedCell(float x, float yTop, int fontSize, List<String> lines, float lineHeight) {
            if (lines == null || lines.isEmpty()) {
                doc.drawText(x, yTop, fontSize, "-", false);
                return;
            }
            float currentY = yTop;
            for (String line : lines) {
                doc.drawText(x, currentY, fontSize, line, false);
                currentY += lineHeight;
            }
        }

        /**
         * 格式化数值。
         */
        private String formatNumber(Double value) {
            if (value == null) {
                return "-";
            }
            return formatNumber(value.doubleValue());
        }

        /**
         * 格式化数值。
         */
        private String formatNumber(double value) {
            return String.format(Locale.ROOT, "%.4f", value);
        }

        /**
         * 格式化时间标签。
         */
        private String formatTimeLabel(long millis) {
            return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_LABEL_FORMATTER);
        }

        /**
         * 格式化分析模式。
         */
        private String formatModeLabel(String analysisMode) {
            return "STRUCTURED".equalsIgnoreCase(analysisMode) ? "结构化分析" : "时序分析";
        }

        /**
         * 刷新折线段。
         */
        private void flushPolyline(List<float[]> points, float[] color, boolean close) {
            if (!close || points.size() < 2) {
                points.clear();
                return;
            }
            doc.setStrokeColor(color[0], color[1], color[2]);
            doc.setLineWidth(1.1f);
            doc.drawPolyline(points);
            points.clear();
        }

        /**
         * 计算数值范围。
         */
        private DoubleRange resolveRange(List<Double> values) {
            double min = 0d;
            double max = 1d;
            boolean initialized = false;
            for (Double value : values) {
                if (value == null) {
                    continue;
                }
                if (!initialized) {
                    min = value;
                    max = value;
                    initialized = true;
                } else {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
            if (!initialized) {
                return new DoubleRange(0d, 1d);
            }
            if (Double.compare(min, max) == 0) {
                return new DoubleRange(min - 0.5d, max + 0.5d);
            }
            return new DoubleRange(min, max);
        }
    }

    private static final class PdfDocument {
        private static final Path[] FONT_CANDIDATES = new Path[] {
            windowsFont("simhei.ttf"),
            windowsFont("STXIHEI.TTF"),
            windowsFont("msyh.ttc"),
            Paths.get("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
            Paths.get("/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf"),
            Paths.get("/System/Library/Fonts/PingFang.ttc")
        };

        private final float width;
        private final float height;
        private final PDDocument document;
        private final List<PDPage> pages = new ArrayList<>();
        private final PDFont regularFont;
        private final PDFont boldFont;
        private PDPage currentPage;
        private PDPageContentStream currentStream;

        private PdfDocument(float width, float height) {
            this.width = width;
            this.height = height;
            this.document = new PDDocument();
            this.regularFont = loadPreferredFont(document);
            this.boldFont = regularFont;
            newPage();
        }

        /**
         * 新建页面。
         */
        private void newPage() {
            closeCurrentStreamQuietly();
            currentPage = new PDPage(new PDRectangle(width, height));
            document.addPage(currentPage);
            pages.add(currentPage);
            currentStream = createContentStream(currentPage, false);
        }

        /**
         * 设置描边颜色。
         */
        private void setStrokeColor(float r, float g, float b) {
            try {
                currentStream.setStrokingColor(clampColor(r), clampColor(g), clampColor(b));
            } catch (IOException ex) {
                throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("设置描边颜色失败", ex), ex);
            }
        }

        /**
         * 设置填充颜色。
         */
        private void setFillColor(float r, float g, float b) {
            try {
                currentStream.setNonStrokingColor(clampColor(r), clampColor(g), clampColor(b));
            } catch (IOException ex) {
                throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("设置填充颜色失败", ex), ex);
            }
        }

        /**
         * 设置线宽。
         */
        private void setLineWidth(float width) {
            try {
                currentStream.setLineWidth(width);
            } catch (IOException ex) {
                throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("设置线宽失败", ex), ex);
            }
        }

        /**
         * 绘制直线。
         */
        private void drawLine(float x1, float y1Top, float x2, float y2Top) {
            try {
                currentStream.moveTo(x1, toPdfY(y1Top));
                currentStream.lineTo(x2, toPdfY(y2Top));
                currentStream.stroke();
            } catch (IOException ex) {
                throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("绘制直线失败", ex), ex);
            }
        }

        /**
         * 绘制矩形。
         */
        private void drawRect(float x, float yTop, float width, float height, boolean fill, boolean stroke) {
            try {
                currentStream.addRect(x, toPdfY(yTop + height), width, height);
                if (fill && stroke) {
                    currentStream.fillAndStroke();
                } else if (fill) {
                    currentStream.fill();
                } else if (stroke) {
                    currentStream.stroke();
                }
            } catch (IOException ex) {
                throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("绘制矩形失败", ex), ex);
            }
        }

        /**
         * 绘制文本。
         */
        private void drawText(float x, float yTop, int fontSize, String text, boolean bold) {
            if (text == null) {
                return;
            }
            try {
                PDFont font = bold ? boldFont : regularFont;
                currentStream.beginText();
                currentStream.setFont(font, fontSize);
                currentStream.newLineAtOffset(x, toPdfY(yTop));
                currentStream.showText(text);
                currentStream.endText();
            } catch (IOException ex) {
                throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("绘制文本失败", ex), ex);
            }
        }

        /**
         * 绘制折线。
         */
        private void drawPolyline(List<float[]> points) {
            if (points == null || points.size() < 2) {
                return;
            }
            try {
                float[] first = points.get(0);
                currentStream.moveTo(first[0], toPdfY(first[1]));
                for (int i = 1; i < points.size(); i++) {
                    float[] point = points.get(i);
                    currentStream.lineTo(point[0], toPdfY(point[1]));
                }
                currentStream.stroke();
            } catch (IOException ex) {
                throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("绘制折线失败", ex), ex);
            }
        }

        /**
         * 估算文本宽度。
         */
        private float estimateTextWidth(String text, float fontSize) {
            if (text == null || text.isEmpty()) {
                return 0f;
            }
            try {
                return regularFont.getStringWidth(text) / 1000f * fontSize;
            } catch (IOException ex) {
                return text.length() * fontSize * 0.8f;
            }
        }

        /**
         * 追加页码。
         */
        private void appendPageNumbers() {
            closeCurrentStreamQuietly();
            int total = pages.size();
            for (int index = 0; index < total; index++) {
                String text = String.format(Locale.ROOT, "Page %d / %d", index + 1, total);
                float x = width - MARGIN_X - estimateTextWidth(text, 9f);
                float y = height - MARGIN_BOTTOM + 16f;
                try (PDPageContentStream stream = createContentStream(pages.get(index), true)) {
                    stream.setNonStrokingColor(clampColor(0.45f), clampColor(0.48f), clampColor(0.52f));
                    stream.beginText();
                    stream.setFont(regularFont, 9f);
                    stream.newLineAtOffset(x, toPdfY(y));
                    stream.showText(text);
                    stream.endText();
                } catch (IOException ex) {
                    throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("追加页码失败", ex), ex);
                }
            }
        }

        /**
         * 构建 PDF 字节数组。
         */
        private byte[] build() {
            closeCurrentStreamQuietly();
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.save(output);
                document.close();
                return output.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("构建PDF失败", ex), ex);
            }
        }

        /**
         * 顶部坐标转换为 PDF 坐标。
         */
        private float toPdfY(float yTop) {
            return height - yTop;
        }

        /**
         * 创建页面内容流。
         */
        private PDPageContentStream createContentStream(PDPage page, boolean append) {
            try {
                if (append) {
                    return new PDPageContentStream(document, page, AppendMode.APPEND, true, true);
                }
                return new PDPageContentStream(document, page);
            } catch (IOException ex) {
                throw new IllegalStateException(ExceptionMessageUtils.buildDetailedMessage("创建PDF内容流失败", ex), ex);
            }
        }

        /**
         * 安静关闭当前内容流。
         */
        private void closeCurrentStreamQuietly() {
            if (currentStream == null) {
                return;
            }
            try {
                currentStream.close();
            } catch (IOException ignored) {
            } finally {
                currentStream = null;
            }
        }

        /**
         * 加载首选字体，优先使用系统黑体。
         */
        private PDFont loadPreferredFont(PDDocument document) {
            for (Path candidate : FONT_CANDIDATES) {
                if (candidate == null || !Files.exists(candidate)) {
                    continue;
                }
                try (InputStream inputStream = Files.newInputStream(candidate)) {
                    return PDType0Font.load(document, inputStream, true);
                } catch (IOException ignored) {
                }
            }
            return PDType1Font.HELVETICA;
        }

        /**
         * 获取 Windows 字体目录下的字体路径。
         */
        private static Path windowsFont(String fileName) {
            String windir = System.getenv("WINDIR");
            if (windir != null && !windir.isBlank()) {
                return Paths.get(windir, "Fonts", fileName);
            }
            return Paths.get("C:/Windows/Fonts", fileName);
        }

        /**
         * 规范化颜色分量。
         */
        private int clampColor(float value) {
            int scaled = Math.round(value * 255f);
            return Math.max(0, Math.min(255, scaled));
        }
    }
}
