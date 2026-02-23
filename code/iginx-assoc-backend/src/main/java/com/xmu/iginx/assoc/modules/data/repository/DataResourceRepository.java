package com.xmu.iginx.assoc.modules.data.repository;

import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface DataResourceRepository extends JpaRepository<DataResourceEntity, Long>, JpaSpecificationExecutor<DataResourceEntity> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Optional<DataResourceEntity> findByMountPath(String mountPath);
}
