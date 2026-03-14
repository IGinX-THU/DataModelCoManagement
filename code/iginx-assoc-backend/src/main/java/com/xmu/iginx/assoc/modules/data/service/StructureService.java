package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.MeasurementRequest;
import com.xmu.iginx.assoc.modules.data.dto.StorageGroupRequest;
import com.xmu.iginx.assoc.modules.data.dto.TableCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.TableDropRequest;
import com.xmu.iginx.assoc.modules.data.vo.TableColumnVO;

import java.util.List;

/**
 * 结构维护服务接口。
 */
public interface StructureService {

    /**
     * 查询表字段信息。
     *
     * @param sourceId 数据源 ID
     * @param schema schema 名称
     * @param table 表名
     * @return 字段列表
     */
    List<TableColumnVO> listTableColumns(Long sourceId, String schema, String table);

    /**
     * 创建存储组。
     *
     * @param request 请求参数
     */
    void createStorageGroup(StorageGroupRequest request);

    /**
     * 删除存储组。
     *
     * @param request 请求参数
     */
    void dropStorageGroup(StorageGroupRequest request);

    /**
     * 创建测点。
     *
     * @param request 请求参数
     */
    void createMeasurement(MeasurementRequest request);

    /**
     * 删除测点。
     *
     * @param request 请求参数
     */
    void dropMeasurement(MeasurementRequest request);

    /**
     * 创建结构化表。
     *
     * @param request 请求参数
     */
    void createTable(TableCreateRequest request);

    /**
     * 删除结构化表。
     *
     * @param request 请求参数
     */
    void dropTable(TableDropRequest request);
}
