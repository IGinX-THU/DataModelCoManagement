package com.xmu.iginx.assoc.modules.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 统一数据导入请求。
 * <p>
 * 仅保留三个核心输入：目标路径、KEY 方式、KEY 列名（按需）。
 * </p>
 */
@Data
public class DataImportRequest {

    /**
     * 导入目标路径，例如：`ts.demo.predict_power` 或 `rt.biz.order`。
     */
    @NotBlank(message = "导入目标路径不能为空")
    private String targetPath;

    /**
     * KEY 方式：自动生成 或 使用指定列作为 KEY。
     */
    @NotNull(message = "KEY方式不能为空")
    private KeyMode keyMode = KeyMode.AUTO_GENERATED;

    /**
     * KEY 列名，仅当 KEY 方式为 {@link KeyMode#COLUMN} 时必填。
     */
    private String keyColumn;

    /**
     * 导入 KEY 生成方式枚举。
     */
    public enum KeyMode {
        /**
         * 不指定 KEY，交由 IGinX 自动从 0 开始生成。
         */
        AUTO_GENERATED,
        /**
         * 使用 CSV 的某一列作为 KEY（对应 SQL：SET KEY "colName"）。
         */
        COLUMN
    }
}
