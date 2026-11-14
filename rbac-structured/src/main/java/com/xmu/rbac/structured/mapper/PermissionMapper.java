package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Permission的Spring Data JPA持久化抽象。 */
public interface PermissionMapper
    extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {}
