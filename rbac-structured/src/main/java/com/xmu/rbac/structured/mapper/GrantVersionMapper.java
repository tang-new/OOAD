package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.GrantVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 全局授权版本持久化接口，所有授权写操作首先持有该行排他锁。 */
public interface GrantVersionMapper extends JpaRepository<GrantVersion, Long> {
    /** 在调用事务内锁定授权版本，提交或回滚时释放。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrantVersion g where g.id = ?1")
    GrantVersion lockById(Long id);
}
