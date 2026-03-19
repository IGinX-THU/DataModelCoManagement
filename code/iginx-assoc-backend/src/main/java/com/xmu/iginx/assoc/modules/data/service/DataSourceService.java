package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.common.PageResult;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceQueryRequest;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceVO;

/**
 * 数据源服务接口。
 */
public interface DataSourceService {

    /**
     * 创建数据源。
     *
     * @param request 创建请求
     * @return 数据源 ID
     */
    Long createDataSource(DataSourceCreateRequest request);

    /**
     * 卸载数据源并移除对应存储引擎配置。
     *
     * @param id 数据源 ID
     */
    void uninstallDataSource(Long id);

    /**
     * 分页查询数据源。
     *
     * @param request 查询请求
     * @return 分页结果
     */
    PageResult<DataSourceVO> pageDataSources(DataSourceQueryRequest request);

    /**
     * 获取数据源详情。
     *
     * @param id 数据源 ID
     * @return 数据源信息
     */
    DataSourceVO getDataSource(Long id);

    /**
     * 测试数据源连接。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     */
    void testConnection(String sourceType, com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig config);
}
