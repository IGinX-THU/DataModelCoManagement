package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.common.PageResult;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceQueryRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceUpdateRequest;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceStructureNodeVO;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceVO;

import java.util.List;

public interface DataSourceService {

    Long createDataSource(DataSourceCreateRequest request);

    PageResult<DataSourceVO> pageDataSources(DataSourceQueryRequest request);

    DataSourceVO getDataSource(Long id);

    void updateDataSource(Long id, DataSourceUpdateRequest request);

    void removeDataSource(Long id, boolean force);

    void testConnection(String sourceType, com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig config);

    List<DataSourceStructureNodeVO> listStructure(Long id);
}
