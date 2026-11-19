package com.xmu.rbac.ddd.interfaces.rest.dto;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.xmu.rbac.ddd.application.UiResult;
import java.util.List;

/** UI 公开响应，子树由 REST 边界组装。 */
@CopyFrom(UiResult.class)
public record UiResourceView(
        Long id,
        String systemCode,
        Long parentId,
        String type,
        String code,
        String name,
        String route,
        String component,
        Integer sortOrder,
        String status,
        String permissionCode,
        List<UiResourceView> children) {
}
