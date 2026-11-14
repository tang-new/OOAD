package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.domain.port.DirectoryRepository.*;

import java.util.*;

/** 输出仅白名单字段，人员电话按最小披露原则统一脱敏。 */
public final class DirectoryViews {
    private DirectoryViews() {}

    public static Map<String, Object> system(SystemDefinition s) {
        return Map.of("id", s.id(), "code", s.code(), "name", s.name(), "status", s.status());
    }

    public static Map<String, Object> user(Profile p) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", p.id());
        view.put("subject", p.subject());
        view.put("username", p.username());
        view.put("name", p.name());
        view.put("no", p.no());
        view.put(
                "phone",
                p.phone() == null ? null : p.phone().length() > 7
                    ? p.phone().substring(0, 3) + "****" + p.phone().substring(p.phone().length() - 4)
                    : "****");
        view.put("departmentId", p.departmentId());
        view.put("departmentName", p.departmentName());
        view.put("status", p.status());
        view.put("version", p.version());
        return view;
    }

    public static List<Map<String, Object>> departments(List<Department> values) {
        Set<Long> ids = new HashSet<>();
        for (Department d : values) ids.add(d.id());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Department d : values)
            if (d.parentId() == null || !ids.contains(d.parentId()))
                result.add(department(d, values));
        return result;
    }

    private static Map<String, Object> department(Department d, List<Department> all) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", d.id());
        view.put("parentId", d.parentId());
        view.put("code", d.code());
        view.put("name", d.name());
        view.put("path", d.path());
        view.put("status", d.status());
        view.put(
                "children",
                all.stream()
                        .filter(child -> d.id().equals(child.parentId()))
                        .map(child -> department(child, all))
                        .toList());
        return view;
    }
}
