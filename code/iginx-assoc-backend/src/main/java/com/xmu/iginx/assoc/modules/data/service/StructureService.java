package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.modules.data.dto.MeasurementRequest;
import com.xmu.iginx.assoc.modules.data.dto.StorageGroupRequest;
import com.xmu.iginx.assoc.modules.data.dto.TableCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.TableDropRequest;
import com.xmu.iginx.assoc.modules.data.vo.TableColumnVO;

import java.util.List;

public interface StructureService {

    List<TableColumnVO> listTableColumns(Long sourceId, String schema, String table);

    void createStorageGroup(StorageGroupRequest request);

    void dropStorageGroup(StorageGroupRequest request);

    void createMeasurement(MeasurementRequest request);

    void dropMeasurement(MeasurementRequest request);

    void createTable(TableCreateRequest request);

    void dropTable(TableDropRequest request);
}
