package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** RolePermission的Spring Data JPA持久化抽象。 */
public interface RolePermissionMapper
    extends JpaRepository<RolePermission, Long>, JpaSpecificationExecutor<RolePermission> {}
