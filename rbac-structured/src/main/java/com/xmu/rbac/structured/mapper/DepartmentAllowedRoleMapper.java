package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.DepartmentAllowedRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** DepartmentAllowedRole的Spring Data JPA持久化抽象。 */
public interface DepartmentAllowedRoleMapper
    extends JpaRepository<DepartmentAllowedRole, Long>,
            JpaSpecificationExecutor<DepartmentAllowedRole> {}
