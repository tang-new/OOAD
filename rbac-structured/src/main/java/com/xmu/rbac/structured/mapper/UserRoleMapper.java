package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** UserRole的Spring Data JPA持久化抽象。 */
public interface UserRoleMapper
    extends JpaRepository<UserRole, Long>, JpaSpecificationExecutor<UserRole> {}
