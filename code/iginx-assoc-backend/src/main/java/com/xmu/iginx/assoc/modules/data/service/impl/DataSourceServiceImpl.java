package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.ClusterInfo;
import cn.edu.tsinghua.iginx.thrift.StorageEngineInfo;
import com.xmu.iginx.assoc.common.PageResult;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceCreateRequest;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceQueryRequest;
import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.repository.DataResourceRepository;
import com.xmu.iginx.assoc.modules.data.service.DataSourceConnectionTestService;
import com.xmu.iginx.assoc.modules.data.service.DataSourceService;
import com.xmu.iginx.assoc.modules.data.util.ConnectionConfigCipher;
import com.xmu.iginx.assoc.modules.data.util.IginxStorageEngineHelper;
import com.xmu.iginx.assoc.modules.data.util.StorageEngineFlagsValidator;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceConnectionConfigVO;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceDetailVO;
import com.xmu.iginx.assoc.modules.data.vo.DataSourceVO;
import com.xmu.iginx.assoc.modules.data.vo.StorageEngineVO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 数据源服务实现，负责数据源的增查与连接校验等能力。
 */
@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

    private final DataResourceRepository dataResourceRepository;
    private final DataSourceConnectionTestService connectionTestService;
    private final ConnectionConfigCipher connectionConfigCipher;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final IginxStorageEngineHelper storageEngineHelper;

    /**
     * 新增数据源并完成必要的存储引擎注册。
     *
     * @param request 新增请求
     * @return 数据源 ID
     */
    @Override
    @Transactional
    public Long createDataSource(DataSourceCreateRequest request) {
        validateRequest(request.getSourceType(), request.getConnectionConfig());
        validateUniqueName(request.getName(), null);
        // 先进行连接性校验，避免无效配置落库
        connectionTestService.testConnection(request.getSourceType(), request.getConnectionConfig());
        DataSourceType sourceType = resolveSourceType(request.getSourceType());
        if (!storageEngineExists(sourceType, request.getConnectionConfig())) {
            // 仅当 IGinX 未注册该引擎时才执行注册
            String addSql = storageEngineHelper.buildAddStorageEngineSql(
                sourceType,
                request.getConnectionConfig());
            iginxStorageWrapper.executeSql(addSql);
        }

        DataResourceEntity entity = new DataResourceEntity();
        entity.setName(request.getName());
        entity.setSourceType(request.getSourceType().toUpperCase());
        entity.setDescription(request.getDescription());
        entity.setConnConfig(connectionConfigCipher.encrypt(request.getConnectionConfig()));
        entity.setCreateTime(LocalDateTime.now());

        DataResourceEntity saved = dataResourceRepository.save(entity);
        return saved.getId();
    }

    /**
     * 分页查询数据源列表。
     *
     * @param request 查询请求
     * @return 分页结果
     */
    @Override
    public PageResult<DataSourceVO> pageDataSources(DataSourceQueryRequest request) {
        Pageable pageable = PageRequest.of(request.getPageNum() - 1, request.getPageSize());
        Specification<DataResourceEntity> specification = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (request.getName() != null && !request.getName().isBlank()) {
                predicates = cb.and(predicates, cb.like(root.get("name"), "%" + request.getName().trim() + "%"));
            }
            if (request.getSourceType() != null && !request.getSourceType().isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("sourceType"), request.getSourceType().trim().toUpperCase()));
            }
            return predicates;
        };

        Page<DataResourceEntity> page = dataResourceRepository.findAll(specification, pageable);
        return PageResult.of(
            page.getContent().stream().map(this::toVO).toList(),
            page.getTotalElements(),
            request.getPageNum(),
            request.getPageSize()
        );
    }

    /**
     * 获取单个数据源详情。
     *
     * @param id 数据源 ID
     * @return 数据源信息
     */
    @Override
    public DataSourceVO getDataSource(Long id) {
        DataResourceEntity entity = findById(id);
        return toVO(entity);
    }

    /**
     * 获取数据源详情（聚合）。
     *
     * @param id 数据源 ID
     * @param limit 兼容参数，当前不再返回路径列表
     * @return 详情聚合视图
     */
    @Override
    public DataSourceDetailVO getDetail(Long id, Integer limit) {
        DataSourceVO meta = getDataSource(id);
        List<StorageEngineVO> engines = listStorageEngines();

        DataSourceDetailVO detail = new DataSourceDetailVO();
        detail.setMeta(meta);
        detail.setEngines(engines);
        return detail;
    }

    /**
     * 测试数据源连接是否可用。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     */
    @Override
    public void testConnection(String sourceType, DataSourceConnectionConfig config) {
        validateRequest(sourceType, config);
        connectionTestService.testConnection(sourceType, config);
    }

    private List<StorageEngineVO> listStorageEngines() {
        return iginxStorageWrapper.executeWithSession(session -> {
            ClusterInfo clusterInfo = session.getClusterInfo();
            List<StorageEngineInfo> infos = clusterInfo == null ? List.of() : clusterInfo.getStorageEngineInfos();
            if (infos == null || infos.isEmpty()) {
                return List.of();
            }
            List<StorageEngineVO> engines = new ArrayList<>();
            for (StorageEngineInfo info : infos) {
                if (info == null) {
                    continue;
                }
                StorageEngineVO engine = new StorageEngineVO();
                engine.setIp(info.getIp());
                engine.setPort(info.getPort());
                engine.setType(info.getType() == null ? "" : info.getType().toString());
                engine.setSchemaPrefix(info.getSchemaPrefix());
                engine.setDataPrefix(info.getDataPrefix());
                engines.add(engine);
            }
            return engines;
        });
    }

    /**
     * 校验数据源类型与连接配置的基本合法性。
     *
     * @param sourceType 数据源类型
     * @param connectionConfig 连接配置
     */
    private void validateRequest(String sourceType, DataSourceConnectionConfig connectionConfig) {
        if (!DataSourceType.isSupported(sourceType)) {
            throw BizException.badRequest("不支持的数据源类型: " + sourceType);
        }
        if (connectionConfig == null) {
            throw BizException.badRequest("连接配置不能为空");
        }
        StorageEngineFlagsValidator.validate(connectionConfig);
    }

    /**
     * 校验数据源名称在库中唯一。
     *
     * @param name 数据源名称
     * @param id 当前数据源 ID（更新时用于排除自身）
     */
    private void validateUniqueName(String name, Long id) {
        boolean exists = id == null
            ? dataResourceRepository.existsByName(name)
            : dataResourceRepository.existsByNameAndIdNot(name, id);
        if (exists) {
            throw BizException.badRequest("数据源名称已存在");
        }
    }

    /**
     * 将字符串解析为数据源类型枚举。
     *
     * @param sourceType 数据源类型字符串
     * @return 枚举类型，无法解析时返回 null
     */
    private DataSourceType resolveSourceType(String sourceType) {
        if (sourceType == null) {
            return null;
        }
        try {
            return DataSourceType.valueOf(sourceType.trim().toUpperCase());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 按 ID 获取数据源实体，不存在则抛异常。
     *
     * @param id 数据源 ID
     * @return 数据源实体
     */
    private DataResourceEntity findById(Long id) {
        return dataResourceRepository.findById(id)
            .orElseThrow(() -> BizException.badRequest("数据源不存在，id=" + id));
    }

    /**
     * 判断指定存储引擎是否已注册。
     *
     * @param sourceType 数据源类型
     * @param config 连接配置
     * @return 是否已注册
     */
    private boolean storageEngineExists(DataSourceType sourceType,
                                        DataSourceConnectionConfig config) {
        String resolvedHost = storageEngineHelper.resolveStorageHost(config.getHost());
        String engineType = storageEngineHelper.resolveEngineType(sourceType);
        int port = config.getPort() == null ? -1 : config.getPort();
        try {
            return iginxStorageWrapper.executeWithSession(session -> {
                ClusterInfo clusterInfo = session.getClusterInfo();
                List<StorageEngineInfo> infos = clusterInfo == null ? null : clusterInfo.getStorageEngineInfos();
                if (infos == null || infos.isEmpty()) {
                    return false;
                }
                for (StorageEngineInfo info : infos) {
                    String infoType = info.getType() == null ? "" : info.getType().toString();
                    if (!engineType.equalsIgnoreCase(infoType)) {
                        continue;
                    }
                    if (resolvedHost == null || info.getIp() == null) {
                        continue;
                    }
                    if (!resolvedHost.equalsIgnoreCase(info.getIp())
                        && !isHostAliasMatch(config.getHost(), info.getIp())) {
                        continue;
                    }
                    if (info.getPort() != port) {
                        continue;
                    }
                    return true;
                }
                return false;
            });
        } catch (BizException ex) {
            if (isClusterInfoIncompatible(ex)) {
                // 兼容旧版 IGinX：无法读取集群信息时默认按未注册处理
                return false;
            }
            throw ex;
        }
    }

    /**
     * 判断是否为集群信息不兼容异常。
     *
     * @param ex 业务异常
     * @return 是否不兼容
     */
    private boolean isClusterInfoIncompatible(BizException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("connectable") && lower.contains("iginxinfo");
    }

    /**
     * 判断主机别名是否匹配（处理本地与 Docker 场景）。
     *
     * @param rawHost 配置主机
     * @param actualHost 实际主机
     * @return 是否匹配
     */
    private boolean isHostAliasMatch(String rawHost, String actualHost) {
        if (rawHost == null || actualHost == null) {
            return false;
        }
        String raw = rawHost.trim().toLowerCase(Locale.ROOT);
        String actual = actualHost.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty() || actual.isEmpty()) {
            return false;
        }
        if (("127.0.0.1".equals(raw) || "localhost".equals(raw))
            && ("host.docker.internal".equals(actual) || "192.168.65.254".equals(actual))) {
            return true;
        }
        if ("host.docker.internal".equals(raw) && "host.docker.internal".equals(actual)) {
            return true;
        }
        if ("host.docker.internal".equals(raw) && actual.equals("192.168.65.254")) {
            return true;
        }
        return false;
    }

    /**
     * 将数据源实体转换为视图对象。
     *
     * @param entity 实体
     * @return 视图对象
     */
    private DataSourceVO toVO(DataResourceEntity entity) {
        DataSourceConnectionConfig config = connectionConfigCipher.decrypt(entity.getConnConfig());

        DataSourceConnectionConfigVO configVO = new DataSourceConnectionConfigVO();
        configVO.setHost(config.getHost());
        configVO.setPort(config.getPort());
        configVO.setDatabase(config.getDatabase());
        configVO.setUsername(config.getUsername());
        configVO.setPasswordMasked(connectionConfigCipher.maskPassword(entity.getConnConfig()));
        configVO.setHasData(config.getHasData());
        configVO.setReadOnly(config.getReadOnly());
        configVO.setExtra(config.getExtra());

        DataSourceVO vo = new DataSourceVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setSourceType(entity.getSourceType());
        vo.setDescription(entity.getDescription());
        vo.setCreateTime(entity.getCreateTime());
        vo.setConnectionConfig(configVO);
        return vo;
    }
}
