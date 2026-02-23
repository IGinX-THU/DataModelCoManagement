package com.xmu.iginx.assoc.modules.data.service;

import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.model.DataSourceDetail;
import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.data.util.ConnectionConfigCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class DataSourceAccessor {

    private final DataResourceRepository dataResourceRepository;
    private final ConnectionConfigCipher connectionConfigCipher;

    public DataSourceDetail getDetail(Long id) {
        DataResourceEntity entity = dataResourceRepository.findById(id)
            .orElseThrow(() -> BizException.badRequest("数据源不存在，id=" + id));
        DataSourceType type;
        try {
            type = DataSourceType.valueOf(entity.getSourceType().toUpperCase());
        } catch (Exception ex) {
            throw BizException.badRequest("不支持的数据源类型: " + entity.getSourceType());
        }
        DataSourceConnectionConfig config = connectionConfigCipher.decrypt(entity.getConnConfig());
        return new DataSourceDetail(entity, type, config);
    }

    public DataSourceDetail getDetail(Long id, DataSourceType... allowedTypes) {
        DataSourceDetail detail = getDetail(id);
        if (allowedTypes == null || allowedTypes.length == 0) {
            return detail;
        }
        boolean match = Arrays.stream(allowedTypes).anyMatch(type -> type == detail.type());
        if (!match) {
            throw BizException.badRequest("数据源类型不匹配");
        }
        return detail;
    }
}
