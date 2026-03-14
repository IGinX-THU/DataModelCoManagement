package com.xmu.iginx.assoc.modules.model.repository;

import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 模型版本数据访问接口。
 */
public interface ModelAssetRepository extends JpaRepository<ModelAssetEntity, Long> {

    /**
     * 按档案 ID 查询版本列表（按上传时间升序）。
     *
     * @param profileId 档案 ID
     * @return 版本列表
     */
    List<ModelAssetEntity> findByProfileIdOrderByUploadTimeAsc(Long profileId);

    /**
     * 查询指定档案的最新版本。
     *
     * @param profileId 档案 ID
     * @return 最新版本
     */
    Optional<ModelAssetEntity> findFirstByProfileIdAndIsLatestTrue(Long profileId);

    /**
     * 判断指定版本是否已存在。
     *
     * @param profileId 档案 ID
     * @param version 版本号
     * @return 是否存在
     */
    boolean existsByProfileIdAndVersion(Long profileId, String version);

    /**
     * 查询指定档案的所有版本。
     *
     * @param profileId 档案 ID
     * @return 版本列表
     */
    List<ModelAssetEntity> findByProfileId(Long profileId);
}
