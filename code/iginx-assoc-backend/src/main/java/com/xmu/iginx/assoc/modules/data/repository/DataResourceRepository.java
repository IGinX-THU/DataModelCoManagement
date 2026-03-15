package com.xmu.iginx.assoc.modules.data.repository;

import com.xmu.iginx.assoc.modules.data.entity.DataResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 数据源资源仓库。
 */
public interface DataResourceRepository extends JpaRepository<DataResourceEntity, Long>, JpaSpecificationExecutor<DataResourceEntity> {

    /**
     * 判断名称是否存在。
     *
     * @param name 数据源名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 判断名称是否存在（排除指定 ID）。
     *
     * @param name 数据源名称
     * @param id 排除的 ID
     * @return 是否存在
     */
    boolean existsByNameAndIdNot(String name, Long id);
}
