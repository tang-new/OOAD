package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Department的Spring Data JPA持久化抽象。 */
public interface DepartmentMapper
    extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {
    /** 只判断是否存在可浏览子部门，避免装载整棵树。 */
    boolean existsByParentIdAndStatus(Long parentId, String status);
}
