package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.UiResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** UiResource的Spring Data JPA持久化抽象。 */
public interface UiResourceMapper
    extends JpaRepository<UiResource, Long>, JpaSpecificationExecutor<UiResource> {}
