package com.xmu.rbac.contract;

import java.util.Set;

/** 仪表盘聚合三个目录的计数，调用者须同时拥有三项查看权限。 */
public final class DashboardAccess {
    public static final String SYSTEM = "rbac-center";
    public static final String OPERATION = "getDashboard";
    public static final Set<String> REQUIRED_PERMISSIONS =
            Set.of("rbac:user:view", "rbac:role:view", "rbac:system:view");

    private DashboardAccess() {}

    public static boolean matches(String system, String operation) {
        return SYSTEM.equals(system) && OPERATION.equals(operation);
    }
}
