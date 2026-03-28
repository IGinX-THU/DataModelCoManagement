package com.xmu.iginx.assoc.modules.model.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.iginx.assoc.modules.model.dto.ModelUploadRequest;
import com.xmu.iginx.assoc.modules.model.entity.MetaModelProfileEntity;
import com.xmu.iginx.assoc.modules.model.repository.MetaModelProfileRepository;
import com.xmu.iginx.assoc.modules.model.repository.ModelAssetRepository;
import com.xmu.iginx.assoc.modules.model.util.ModelFileStorageService;
import com.xmu.iginx.assoc.modules.model.util.ModelFunctionSchemaParser;
import com.xmu.iginx.assoc.modules.model.util.ModelSchemaParser;
import com.xmu.iginx.assoc.modules.relation.repository.AssociationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型资产服务测试。
 */
@ExtendWith(MockitoExtension.class)
class ModelAssetServiceImplTest {

    @Mock
    private MetaModelProfileRepository profileRepository;

    @Mock
    private ModelAssetRepository assetRepository;

    @Mock
    private AssociationRuleRepository associationRuleRepository;

    @Mock
    private ModelFileStorageService fileStorageService;

    private ModelAssetServiceImpl service;

    @BeforeEach
    void setUp() {
        ModelSchemaParser schemaParser = new ModelSchemaParser();
        ModelFunctionSchemaParser functionSchemaParser = new ModelFunctionSchemaParser(schemaParser);
        service = new ModelAssetServiceImpl(
            profileRepository,
            assetRepository,
            associationRuleRepository,
            fileStorageService,
            schemaParser,
            functionSchemaParser,
            new ObjectMapper()
        );
    }

    /**
     * 上传 `.m` 文件且模型类型为 MATLAB 时，应通过后缀校验并按 MAT 存储。
     */
    @Test
    void uploadModel_shouldAcceptMExtensionWhenTypeIsMatlab() throws Exception {
        String script = """
            function result = predict_power(temperature, pressure, flow)
            arguments
                temperature (1,1) double
                pressure (1,1) double
                flow (1,1) double
            end
            result = 0.2 * temperature + 0.05 * pressure + 1.5 * flow;
            end
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "predict_power.m",
            "text/plain",
            script.getBytes(StandardCharsets.UTF_8)
        );
        ModelUploadRequest request = new ModelUploadRequest();
        request.setName("predict_power");
        request.setVersion("v1.0.0");
        request.setType("MATLAB");

        when(profileRepository.findByName("predict_power")).thenReturn(Optional.empty());
        when(profileRepository.save(any(MetaModelProfileEntity.class))).thenAnswer(invocation -> {
            MetaModelProfileEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });
        when(assetRepository.existsByProfileIdAndVersion(1L, "v1.0.0")).thenReturn(false);
        when(assetRepository.findByProfileId(1L)).thenReturn(Collections.emptyList());
        when(assetRepository.findByProfileIdOrderByUploadTimeAsc(1L)).thenReturn(Collections.emptyList());
        when(fileStorageService.store(any(), eq("MAT"), eq(1L), eq("v1.0.0")))
            .thenReturn(new ModelFileStorageService.StoredFile(
                "iginx://models/1/v1.0.0/predict_power.m",
                "fs.models.1.v1_0_0.predict_power_m",
                "predict_power.m",
                "mock-md5"
            ));

        assertDoesNotThrow(() -> service.uploadModel(request, file));
        verify(fileStorageService).store(any(), eq("MAT"), eq(1L), eq("v1.0.0"));
    }
}
