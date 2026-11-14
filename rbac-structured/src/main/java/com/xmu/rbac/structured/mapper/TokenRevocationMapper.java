package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.TokenRevocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** TokenRevocation的Spring Data JPA持久化抽象。 */
public interface TokenRevocationMapper
    extends JpaRepository<TokenRevocation, String>, JpaSpecificationExecutor<TokenRevocation> {}
