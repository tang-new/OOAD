package com.xmu.rbac.ddd.infrastructure.persistence.repository;

import com.xmu.rbac.ddd.infrastructure.persistence.entity.RoleEntity;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** 角色查询由真实 SQL limit/count 分页，不在内存分页。 */
public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {
    @Query(
            "select r from RoleEntity r where r.systemId=:system and (:keyword is null or r.code"
                    + " like :keyword or r.name like :keyword) and (:status is null or"
                    + " r.status=:status)")
    Page<RoleEntity> search(
            @Param("system") Long system,
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable);
}
