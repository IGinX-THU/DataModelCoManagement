package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.common.PageResult;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceQueryRequest;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceDetailVO;
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
     * 获取数据源详情（聚合）。
     *
     * @param id 数据源 ID
     * @param limit 兼容参数，当前不再返回路径列表
     * @return 详情聚合视图
     */
    DataSourceDetailVO getDetail(Long id, Integer limit);

    /**
     * 测试数据源连接。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     */
    void testConnection(String sourceType, com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig config);
}
