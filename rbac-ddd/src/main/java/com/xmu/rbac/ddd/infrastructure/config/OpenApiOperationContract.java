package com.xmu.rbac.ddd.infrastructure.config;

import com.xmu.rbac.ddd.domain.ApiOperation;
import com.xmu.rbac.ddd.domain.port.OperationContract;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/** 从共享合同资源读取精确 operationId，绝不按中文标题推测。 */
@Component
public class OpenApiOperationContract implements OperationContract {
    private final List<ApiOperation> operations;

    @SuppressWarnings("unchecked")
    public OpenApiOperationContract() {
        List<ApiOperation> result = new ArrayList<>();
        Map<String, Object> document =
                yaml().load(
                                Objects.requireNonNull(
                                        getClass().getResourceAsStream("/openapi/rbac-api.yaml")));
        Map<String, Map<String, Object>> paths =
                (Map<String, Map<String, Object>>) document.get("paths");
        for (Map.Entry<String, Map<String, Object>> path : paths.entrySet())
            for (Map.Entry<String, Object> method : path.getValue().entrySet()) {
                if (!Set.of("get", "post", "put", "patch", "delete").contains(method.getKey()))
                    continue;
                Map<String, Object> operation = (Map<String, Object>) method.getValue();
                String required = (String) operation.get("x-required-permission");
                ApiOperation.Policy policy =
                        required == null || required.equals("@authenticated")
                                ? ApiOperation.Policy.AUTHENTICATED
                                : ApiOperation.Policy.PROTECTED;
                if (List.of(
                                "/auth/login",
                                "/.well-known/jwks.json",
                                "/api/v1/actuator/health",
                                "/api/v1/actuator/info")
                        .contains(path.getKey())) policy = ApiOperation.Policy.PUBLIC;
                result.add(
                        new ApiOperation(
                                0,
                                "rbac-center",
                                (String) operation.get("operationId"),
                                method.getKey().toUpperCase(Locale.ROOT),
                                path.getKey(),
                                required == null
                                                || required.startsWith("@")
                                                || required.equals("PUBLIC")
                                        ? null
                                        : required,
                                (String) operation.get("x-required-role"),
                                policy,
                                true));
            }
        operations = List.copyOf(result);
    }

    private static Yaml yaml() {
        org.yaml.snakeyaml.LoaderOptions options = new org.yaml.snakeyaml.LoaderOptions();
        options.setMaxAliasesForCollections(10000);
        return new Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(options));
    }

    public List<ApiOperation> operations() {
        return operations;
    }
}
