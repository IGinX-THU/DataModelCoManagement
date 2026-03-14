package com.xmu.iginx.assoc.modules.relation.repository;

import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 关联规则数据访问接口。
 */
public interface AssociationRuleRepository extends JpaRepository<AssociationRuleEntity, Long> {

    /**
     * 判断数据资源是否被规则引用。
     *
     * @param dataId 数据 ID
     * @return 是否存在引用
     */
    boolean existsByDataId(Long dataId);

    /**
     * 判断模型是否被规则引用。
     *
     * @param modelId 模型 ID
     * @return 是否存在引用
     */
    boolean existsByModelId(Long modelId);

    /**
     * 判断多个模型是否被规则引用。
     *
     * @param modelIds 模型 ID 列表
     * @return 是否存在引用
     */
    boolean existsByModelIdIn(Iterable<Long> modelIds);

    /**
     * 统计指定模型被引用次数。
     *
     * @param modelIds 模型 ID 列表
     * @return 引用数量
     */
    long countByModelIdIn(Iterable<Long> modelIds);
}
