package com.xmu.rbac.ddd.infrastructure.persistence.repository;

import com.xmu.rbac.ddd.infrastructure.persistence.entity.GrantVersionEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

/** 锁与版本写在同一 MySQL 事务内，不用缓存代替权威版本。 */
public interface GrantVersionJpaRepository extends JpaRepository<GrantVersionEntity, Byte> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrantVersionEntity g where g.id=1")
    GrantVersionEntity lockAuthority();

    @Query("select g.version from GrantVersionEntity g where g.id=1")
    Long authorityVersion();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update GrantVersionEntity g set g.version=g.version+1 where g.id=1")
    int increment();
}
