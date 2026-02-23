package com.xmu.iginx.assoc.modules.relation.repository;

import com.xmu.iginx.assoc.modules.relation.entity.AssociationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssociationRuleRepository extends JpaRepository<AssociationRuleEntity, Long> {

    boolean existsByDataId(Long dataId);

    boolean existsByModelId(Long modelId);

    boolean existsByModelIdIn(Iterable<Long> modelIds);

    long countByModelIdIn(Iterable<Long> modelIds);
}
