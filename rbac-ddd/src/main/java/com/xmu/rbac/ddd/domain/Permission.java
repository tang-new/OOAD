package com.xmu.rbac.ddd.domain;

/** 授权种类不能由菜单与 API 映射互相推导。 */
public record Permission(
        long id, String systemCode, String code, String name, Kind kind, boolean enabled) {
    public enum Kind {
        UI,
        API
    }
}
