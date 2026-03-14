package com.xmu.iginx.assoc.modules.model.repository;

import com.xmu.iginx.assoc.modules.model.entity.MetaModelProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 模型档案数据访问接口。
 */
public interface MetaModelProfileRepository extends JpaRepository<MetaModelProfileEntity, Long> {

    /**
     * 判断是否存在指定名称的档案。
     *
     * @param name 档案名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 根据名称查找档案。
     *
     * @param name 档案名称
     * @return 匹配的档案
     */
    Optional<MetaModelProfileEntity> findByName(String name);
}
