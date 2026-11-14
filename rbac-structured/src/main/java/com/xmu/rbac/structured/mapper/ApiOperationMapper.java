package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.ApiOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** ApiOperation的Spring Data JPA持久化抽象。 */
public interface ApiOperationMapper
    extends JpaRepository<ApiOperation, Long>, JpaSpecificationExecutor<ApiOperation> {}
