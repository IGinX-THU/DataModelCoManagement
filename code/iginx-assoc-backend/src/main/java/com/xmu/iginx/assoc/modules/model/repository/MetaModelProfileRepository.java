package com.xmu.iginx.assoc.modules.model.repository;

import com.xmu.iginx.assoc.modules.model.entity.MetaModelProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetaModelProfileRepository extends JpaRepository<MetaModelProfileEntity, Long> {

    boolean existsByName(String name);

    Optional<MetaModelProfileEntity> findByName(String name);
}
