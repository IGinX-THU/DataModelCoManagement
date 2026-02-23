package com.xmu.iginx.assoc.modules.model.repository;

import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelAssetRepository extends JpaRepository<ModelAssetEntity, Long> {

    List<ModelAssetEntity> findByProfileIdOrderByUploadTimeAsc(Long profileId);

    Optional<ModelAssetEntity> findFirstByProfileIdAndIsLatestTrue(Long profileId);

    boolean existsByProfileIdAndVersion(Long profileId, String version);

    List<ModelAssetEntity> findByProfileId(Long profileId);
}
