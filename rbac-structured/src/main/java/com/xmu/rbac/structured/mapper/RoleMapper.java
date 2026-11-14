package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Role的Spring Data JPA持久化抽象。 */
public interface RoleMapper extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {}
