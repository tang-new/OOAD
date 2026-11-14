package com.xmu.rbac.ddd.domain;

import com.xmu.rbac.ddd.domain.port.DirectoryRepository;

import java.util.*;

/** 组织管理范围和部门树不变量，不参与业务对象数据授权。 */
public final class OrganizationPolicy {
    public void requireManaged(String actorPath, DirectoryRepository.Department target) {
        DepartmentEligibility.requireManaged(actorPath, target.path());
    }

    public void requireRoot(String actorPath) {
        DomainException.require(actorPath.equals("/1/"), 403, "ROOT_DEPARTMENT_REQUIRED");
    }

    public void requireDeletable(DirectoryRepository.Department department, boolean occupied) {
        DomainException.require(
                department.parentId() != null && !occupied, 409, "DEPARTMENT_NOT_EMPTY");
    }

    public void requireVersion(Long expected, Long actual) {
        DomainException.require(
                expected == null || expected.equals(actual), 409, "VERSION_CONFLICT");
    }
}
