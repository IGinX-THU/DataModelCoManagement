package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.ClusterInfo;
import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.session.SessionExecuteSqlResult;
import cn.edu.tsinghua.iginx.thrift.DataType;
import cn.edu.tsinghua.iginx.thrift.StorageEngineInfo;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.data.util.ConnectionConfigCipher;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceDetailVO;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceImplDetailTest {

    @Mock
    private DataResourceRepository dataResourceRepository;
    @Mock
    private ConnectionConfigCipher connectionConfigCipher;
    @Mock
    private AssociationRuleRepository associationRuleRepository;
    @Mock
    private IginxStorageWrapper iginxStorageWrapper;
    @Mock
    private com.xmu.iginx.assoc.modules.data.service.DataSourceConnectionTestService connectionTestService;
    @Mock
    private com.xmu.iginx.assoc.modules.data.util.IginxStorageEngineHelper storageEngineHelper;
    @Mock
    private com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper structuredQueryHelper;

    @InjectMocks
    private DataSourceServiceImpl dataSourceService;

    @Test
    void detail_shouldReturnEnginesAndColumnsWithLimit() throws Exception {
        DataResourceEntity entity = new DataResourceEntity();
        entity.setId(1L);
        entity.setName("demo");
        entity.setSourceType("INFLUXDB");
        entity.setConnConfig("enc");

        when(dataResourceRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(connectionConfigCipher.decrypt("enc")).thenReturn(new DataSourceConnectionConfig());

        SessionExecuteSqlResult sqlResult = mock(SessionExecuteSqlResult.class);
        when(sqlResult.getParseErrorMsg()).thenReturn("");
        when(sqlResult.getPaths()).thenReturn(List.of("ts.a", "ts.b"));
        when(sqlResult.getDataTypeList()).thenReturn(List.of(DataType.DOUBLE, DataType.LONG));
        when(iginxStorageWrapper.executeSql("SHOW COLUMNS")).thenReturn(sqlResult);

        StorageEngineInfo engineInfo = mock(StorageEngineInfo.class);
        when(engineInfo.getIp()).thenReturn("127.0.0.1");
        when(engineInfo.getPort()).thenReturn(6667);
        when(engineInfo.getType()).thenReturn(null);
        when(engineInfo.getSchemaPrefix()).thenReturn("");
        when(engineInfo.getDataPrefix()).thenReturn("");

        ClusterInfo clusterInfo = mock(ClusterInfo.class);
        when(clusterInfo.getStorageEngineInfos()).thenReturn(List.of(engineInfo));
        Session session = mock(Session.class);
        when(session.getClusterInfo()).thenReturn(clusterInfo);
        when(iginxStorageWrapper.executeWithSession(any())).thenAnswer(invocation -> {
            var executor = invocation.getArgument(0, IginxStorageWrapper.SessionExecutor.class);
            return executor.apply(session);
        });

        DataSourceDetailVO detail = dataSourceService.getDetail(1L, 1);

        assertEquals(1, detail.getColumns().size());
        assertEquals(1, detail.getEngines().size());
    }
}
