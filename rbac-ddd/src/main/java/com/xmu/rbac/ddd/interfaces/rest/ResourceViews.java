package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.ResourceRepository.Ui;

import java.util.*;

/** 资源输出字段与冻结合同完全一致。 */
public final class ResourceViews {
    private ResourceViews() {}

    public static List<com.xmu.rbac.ddd.interfaces.rest.dto.UiResourceView> tree(List<Ui> values) {
        Set<Long> ids = new HashSet<>();
        for (Ui ui : values) ids.add(ui.id());
        return values.stream()
                .filter(u -> u.parentId() == null || !ids.contains(u.parentId()))
                .map(u -> ui(u, values))
                .toList();
    }

    public static com.xmu.rbac.ddd.interfaces.rest.dto.UiResourceView ui(Ui u, List<Ui> values) {
        com.xmu.rbac.ddd.interfaces.rest.dto.UiResourceView result =
                cn.edu.xmu.clonefactory.util.CloneFactory.copy(
                        new com.xmu.rbac.ddd.interfaces.rest.dto.UiResourceView(),
                        com.xmu.rbac.ddd.application.UiResult.from(u));
        result.setChildren(
                values.stream()
                        .filter(child -> u.id().equals(child.parentId()))
                        .map(child -> ui(child, values))
                        .toList());
        return result;
    }

    public static Map<String, Object> operation(ApiOperation o) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", o.id());
        result.put("systemCode", o.systemCode());
        result.put("operationId", o.operationId());
        result.put("method", o.method());
        result.put("pathTemplate", o.pathTemplate());
        result.put("permissionCode", o.permissionCode());
        result.put("requiredRole", o.requiredRole());
        result.put("accessPolicy", o.policy());
        result.put("enabled", o.enabled());
        return result;
    }
}
