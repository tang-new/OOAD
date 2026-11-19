package com.xmu.rbac.ddd.infrastructure.persistence;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyTo;
import com.xmu.rbac.ddd.domain.port.ResourceRepository.Ui;
import com.xmu.rbac.ddd.infrastructure.persistence.entity.UiResourceEntity;

/** UI 持久化快照，默认状态、排序和权限关联由适配器补齐。 */
@CopyFrom({UiResourceEntity.class, Ui.class})
@CopyTo(UiResourceEntity.class)
public record UiState(
        Long parentId,
        String type,
        String code,
        String name,
        String route,
        String component,
        @CopyFrom.Exclude(Ui.class)
        Integer sortOrder,
        @CopyFrom.Exclude(Ui.class)
        String status,
        Long permissionId) {
}
