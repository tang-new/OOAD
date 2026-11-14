package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 统一用户的Spring Data JPA持久化接口。 */
public interface UserMapper extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    /** 部门调动先锁用户，防止并发调动产生错配指派。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = ?1")
    User lockById(Long id);
}
