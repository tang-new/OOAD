package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.UiApiMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** UiApiMapping的Spring Data JPA持久化抽象。 */
public interface UiApiMappingMapper
    extends JpaRepository<UiApiMapping, Long>, JpaSpecificationExecutor<UiApiMapping> {}
