package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.SessionExecuteSqlResult;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceCreateRequest;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.data.service.DataSourceConnectionTestService;
import com.xmu.iginx.assoc.modules.data.util.ConnectionConfigCipher;
import com.xmu.iginx.assoc.modules.data.util.IginxStorageEngineHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceImplCreateTest {

    @Mock
    private DataResourceRepository dataResourceRepository;
    @Mock
    private DataSourceConnectionTestService connectionTestService;
    @Mock
    private ConnectionConfigCipher connectionConfigCipher;
    @Mock
    private AssociationRuleRepository associationRuleRepository;
    @Mock
    private IginxStorageWrapper iginxStorageWrapper;
    @Mock
    private IginxStorageEngineHelper storageEngineHelper;
    @Mock
    private IginxStructuredQueryHelper structuredQueryHelper;

    @InjectMocks
    private DataSourceServiceImpl dataSourceService;

    @Test
    void createDataSource_shouldAllowEmptyMountPathAndRegisterEngine() {
        DataSourceCreateRequest request = new DataSourceCreateRequest();
        request.setName("pg-demo");
        request.setSourceType("POSTGRESQL");
        request.setMountPath("");

        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHost("127.0.0.1");
        config.setPort(5432);
        request.setConnectionConfig(config);

        when(dataResourceRepository.existsByName("pg-demo")).thenReturn(false);
        when(connectionConfigCipher.encrypt(config)).thenReturn("enc");
        when(storageEngineHelper.buildAddStorageEngineSql(any(), any(), any())).thenReturn("ADD STORAGEENGINE (...)");
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(false);
        when(iginxStorageWrapper.executeSql(anyString())).thenReturn(new SessionExecuteSqlResult());
        when(dataResourceRepository.save(any())).thenAnswer(invocation -> {
            DataResourceEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        Long id = dataSourceService.createDataSource(request);

        assertEquals(1L, id);
        verify(iginxStorageWrapper).executeSql(anyString());
    }
}
