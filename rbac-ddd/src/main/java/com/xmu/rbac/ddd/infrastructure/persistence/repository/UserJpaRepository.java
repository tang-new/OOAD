package com.xmu.rbac.ddd.infrastructure.persistence.repository;

import com.xmu.rbac.ddd.infrastructure.persistence.entity.UserEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 人员真实数据库分页和调动排他锁。 */
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id=:id")
    Optional<UserEntity> lockUser(@Param("id") long id);

    @Query(
            "select u from UserEntity u,DepartmentEntity d where u.departmentId=d.id and d.path"
                + " like :scope and (:keyword is null or u.username like :keyword or u.name like"
                + " :keyword) and (:department is null or u.departmentId=:department) and (:status"
                + " is null or u.status=:status)")
    Page<UserEntity> search(
            @Param("scope") String scope,
            @Param("keyword") String keyword,
            @Param("department") Long department,
            @Param("status") String status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.username=:username")
    Optional<UserEntity> lockLogin(@Param("username") String username);
}
