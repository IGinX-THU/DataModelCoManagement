package com.xmu.iginx.assoc.modules.data.service.impl;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.DataImportRequest;
import com.xmu.iginx.assoc.modules.data.service.DataImportService;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * 统一数据导入服务实现。
 * <p>
 * 当前阶段仅支持 CSV，导入语义统一使用 IGinX SQL：
 * LOAD DATA FROM INFILE ... AS CSV INTO ... [SET KEY "colName"]。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DataImportServiceImpl implements DataImportService {

    private static final String IMPORT_FILE_PREFIX = "dataimport";

    private final IginxStorageWrapper iginxStorageWrapper;
    private final DataFileStorageService fileStorageService;

    /**
     * 执行统一 CSV 导入。
     *
     * @param request 导入请求
     * @param file 导入文件
     * @return 导入结果
     */
    @Override
    public DataImportResultVO importData(DataImportRequest request, MultipartFile file) {
        validateImportRequest(request, file);

        DataFileStorageService.StoredFile storedFile = fileStorageService.createFile(IMPORT_FILE_PREFIX, ".csv");
        Path storedPath = storedFile.path();
        try {
            // 先将上传文件落盘，再交由 IGinX 通过 INFILE 读取。

            file.transferTo(storedPath);

            String keyColumn = resolveKeyColumnByMode(request);
            iginxStorageWrapper.executeLoadDataFromCsv(
                storedPath.toAbsolutePath().toString(),
                request.getTargetPath(),
                keyColumn
            );

            long dataRows = countCsvDataRows(storedPath);
            DataImportResultVO result = new DataImportResultVO();
            result.setTotal(dataRows);
            result.setSuccess(dataRows);
            result.setFailed(0L);
            return result;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.internal("导入 CSV 失败: " + ex.getMessage());
        } finally {
            deleteFileQuietly(storedPath);
        }
    }

    /**
     * 校验导入请求。
     *
     * @param request 导入请求
     * @param file 导入文件
     */
    private void validateImportRequest(DataImportRequest request, MultipartFile file) {
        if (request == null) {
            throw BizException.badRequest("导入请求不能为空");
        }
        if (!StringUtils.hasText(request.getTargetPath())) {
            throw BizException.badRequest("导入目标路径不能为空");
        }
        if (request.getKeyMode() == null) {
            throw BizException.badRequest("KEY方式不能为空");
        }
        if (request.getKeyMode() == DataImportRequest.KeyMode.COLUMN && !StringUtils.hasText(request.getKeyColumn())) {
            throw BizException.badRequest("当 KEY 方式为“使用某一列作为 KEY”时，KEY 列名不能为空");
        }

        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("导入文件不能为空");
        }
        if (!isCsvFile(file.getOriginalFilename())) {
            throw BizException.badRequest("当前仅支持 CSV 文件导入");
        }
    }

    /**
     * 根据 KEY 方式解析 KEY 列。
     *
     * @param request 导入请求
     * @return KEY 列名；为空表示自动生成
     */
    private String resolveKeyColumnByMode(DataImportRequest request) {
        if (request.getKeyMode() == DataImportRequest.KeyMode.COLUMN) {
            return request.getKeyColumn().trim();
        }
        return null;
    }

    /**
     * 判断文件是否为 CSV。
     *
     * @param fileName 文件名
     * @return 是否 CSV
     */
    private boolean isCsvFile(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".csv");
    }

    /**
     * 估算 CSV 数据行数（按“首行是表头”的约定减 1）。
     * <p>
     * 这里采用字节流统计换行数，避免因编码问题读取失败。
     * </p>
     *
     * @param csvPath CSV 文件路径
     * @return 数据行数
     */
    private long countCsvDataRows(Path csvPath) {
        long lineCount = 0L;
        boolean hasAnyByte = false;
        int lastByte = -1;
        try (InputStream inputStream = Files.newInputStream(csvPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                hasAnyByte = true;
                for (int i = 0; i < read; i++) {
                    if (buffer[i] == '\n') {
                        lineCount++;
                    }
                }
                lastByte = buffer[read - 1] & 0xFF;
            }
        } catch (Exception ex) {
            return 0L;
        }

        if (hasAnyByte && lastByte != '\n') {
            lineCount++;
        }
        return Math.max(lineCount - 1, 0L);
    }

    /**
     * 静默删除临时文件，避免导入后残留。
     *
     * @param path 文件路径
     */
    private void deleteFileQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
