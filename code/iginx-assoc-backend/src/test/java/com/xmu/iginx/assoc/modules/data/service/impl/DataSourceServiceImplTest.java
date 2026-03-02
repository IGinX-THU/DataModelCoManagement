package com.xmu.iginx.assoc.modules.data.service.impl;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.data.service.DataSourceConnectionTestService;
import com.xmu.iginx.assoc.modules.data.util.ConnectionConfigCipher;
import com.xmu.iginx.assoc.modules.data.util.IginxStorageEngineHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceImplTest {

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
    void removeDataSource_shouldFallbackToEmptyPrefixesForRemoveHistoryFailure() {
        DataResourceEntity entity = buildEntity(1L, "IOTDB", "tet");
        DataSourceConnectionConfig config = buildConnectionConfig(6669);

        mockCommonDeleteContext(entity, config);
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(true);
        when(storageEngineHelper.buildRemoveStorageEngineSql(eq(config), anyString(), anyString(), eq(false)))
            .thenAnswer(invocation -> {
                String schemaPrefix = invocation.getArgument(1, String.class);
                String dataPrefix = invocation.getArgument(2, String.class);
                return "REMOVE[" + schemaPrefix + "][" + dataPrefix + "]";
            });
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if ("REMOVE[][]".equals(sql)) {
                return null;
            }
            throw BizException.badRequest("remove history data source failed");
        }).when(iginxStorageWrapper).executeSql(anyString());

        assertDoesNotThrow(() -> dataSourceService.removeDataSource(1L, false));

        verify(storageEngineHelper).buildRemoveStorageEngineSql(eq(config), eq(""), eq(""), eq(false));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(iginxStorageWrapper, atLeastOnce()).executeSql(sqlCaptor.capture());
        assertTrue(sqlCaptor.getAllValues().contains("REMOVE[][]"));
        verify(dataResourceRepository).delete(entity);
    }

    @Test
    void removeDataSource_shouldThrowWhenRemoveFailedAndEngineStillExists() {
        DataResourceEntity entity = buildEntity(2L, "IOTDB", "tet");
        DataSourceConnectionConfig config = buildConnectionConfig(6669);

        mockCommonDeleteContext(entity, config);
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(true, true);
        when(storageEngineHelper.buildRemoveStorageEngineSql(eq(config), anyString(), anyString(), eq(false)))
            .thenAnswer(invocation -> {
                String schemaPrefix = invocation.getArgument(1, String.class);
                String dataPrefix = invocation.getArgument(2, String.class);
                return "REMOVE[" + schemaPrefix + "][" + dataPrefix + "]";
            });
        doThrow(BizException.badRequest("remove history data source failed"))
            .when(iginxStorageWrapper).executeSql(anyString());

        assertThrows(BizException.class, () -> dataSourceService.removeDataSource(2L, false));

        verify(storageEngineHelper).buildRemoveStorageEngineSql(eq(config), eq("root.tet"), eq("root.tet"), eq(false));
        verify(dataResourceRepository, never()).delete(entity);
    }

    @Test
    void removeDataSource_shouldUnregisterPostgresqlEngine() {
        DataResourceEntity entity = buildEntity(3L, "POSTGRESQL", "tett");
        DataSourceConnectionConfig config = buildConnectionConfig(5433);

        mockCommonDeleteContext(entity, config);
        when(iginxStorageWrapper.executeWithSession(any())).thenReturn(true);
        when(storageEngineHelper.buildRemoveStorageEngineSql(eq(config), anyString(), anyString(), eq(false)))
            .thenAnswer(invocation -> {
                String schemaPrefix = invocation.getArgument(1, String.class);
                String dataPrefix = invocation.getArgument(2, String.class);
                return "REMOVE[" + schemaPrefix + "][" + dataPrefix + "]";
            });

        assertDoesNotThrow(() -> dataSourceService.removeDataSource(3L, false));

        verify(storageEngineHelper).buildRemoveStorageEngineSql(eq(config), eq("tett"), eq("tett"), eq(false));
        verify(dataResourceRepository).delete(entity);
    }

    private void mockCommonDeleteContext(DataResourceEntity entity, DataSourceConnectionConfig config) {
        when(dataResourceRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(associationRuleRepository.existsByDataId(entity.getId())).thenReturn(false);
        when(connectionConfigCipher.decrypt(entity.getConnConfig())).thenReturn(config);
    }

    private DataResourceEntity buildEntity(Long id, String sourceType, String mountPath) {
        DataResourceEntity entity = new DataResourceEntity();
        entity.setId(id);
        entity.setName(sourceType.toLowerCase() + "-source");
        entity.setSourceType(sourceType);
        entity.setConnConfig("cipher-text-" + id);
        entity.setMountPath(mountPath);
        return entity;
    }

    private DataSourceConnectionConfig buildConnectionConfig(int port) {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig();
        config.setHost("127.0.0.1");
        config.setPort(port);
        config.setDatabase("root");
        config.setUsername("root");
        config.setPassword("root");
        config.setExtra("");
        return config;
    }
}
