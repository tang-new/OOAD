package com.xmu.rbac.structured.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
/** 从唯一OAS读取路由及权限元数据，未知路由拒绝。 */
@Service
public class RouteRegistry {
    public record
        Route(String method, String path, String operationId, String permission, String policy) {}
    private final Map<String, Route> byOperation;
    @SuppressWarnings("unchecked")
    public RouteRegistry() {
        Map<String, Route> registry = new LinkedHashMap<>();
        try (
            java.io.InputStream stream = getClass().getResourceAsStream("/openapi/rbac-api.yaml")) {
            if (stream == null)
                throw new IllegalStateException("缺失OAS");
            org.yaml.snakeyaml.LoaderOptions options = new org.yaml.snakeyaml.LoaderOptions();
            options.setMaxAliasesForCollections(10000);
            Map<String, Object> root =
                new Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(options)).load(stream);
            Map<String, Object> paths = (Map<String, Object>) root.get("paths");
            for (Map.Entry<String, Object> path : paths.entrySet())
                for (Map.Entry<String, Object> method :
                    ((Map<String, Object>) path.getValue()).entrySet()) {
                    if (!Set.of("get", "post", "put", "patch", "delete", "head", "options")
                            .contains(method.getKey()))
                        continue;
                    Map<String, Object> op = (Map<String, Object>) method.getValue();
                    String id = (String) op.get("operationId"),
                           permission = (String) op.get("x-required-permission");
                    if (id == null || permission == null)
                        throw new IllegalStateException("OAS路由缺少安全元数据");
                    String policy = Set.of("PUBLIC", "@public").contains(permission) ? "PUBLIC"
                        : Set.of("@authenticated", "AUTHENTICATED").contains(permission)
                        ? "AUTHENTICATED"
                        : "PROTECTED";
                    if (!policy.equals("PROTECTED"))
                        permission = null;
                    Route route = new Route(method.getKey().toUpperCase(Locale.ROOT), path.getKey(),
                        id, permission, policy);
                    if (registry.put(id, route) != null)
                        throw new IllegalStateException("OAS operationId重复");
                }
            byOperation = Collections.unmodifiableMap(registry);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("OAS不可读", e);
        }
    }
    /** 执行operation操作，保持服务到持久化层的单向依赖。 */
    public Route operation(String id) {
        return byOperation.get(id);
    }
    /** 执行route操作，保持服务到持久化层的单向依赖。 */
    public Route route(String method, String path) {
        return byOperation.values()
            .stream()
            .filter(r -> r.method.equals(method) && r.path.equals(path))
            .findFirst()
            .orElse(null);
    }
    /** 执行all操作，保持服务到持久化层的单向依赖。 */
    public Collection<Route> all() {
        return byOperation.values();
    }
}
