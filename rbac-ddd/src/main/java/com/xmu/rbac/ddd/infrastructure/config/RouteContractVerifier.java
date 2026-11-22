package com.xmu.rbac.ddd.infrastructure.config;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;

/** 启动时双向核验中心实际 Controller、冻结 OAS 及持久化注册表。 */
@Component
public class RouteContractVerifier implements ApplicationRunner {
    private final RequestMappingHandlerMapping routes;
    private final OperationContract contract;
    private final ResourceRepository repository;

    public RouteContractVerifier(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping routes,
            OperationContract contract,
            ResourceRepository repository) {
        this.routes = routes;
        this.contract = contract;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Set<String> actual = new TreeSet<>();
        routes.getHandlerMethods()
                .forEach(
                        (mapping, handler) -> {
                            if (handler.getBeanType()
                                    .getName()
                                    .startsWith("com.xmu.rbac.ddd.interfaces.rest")) {
                                for (String path : mapping.getPatternValues())
                                    mapping.getMethodsCondition()
                                            .getMethods()
                                            .forEach(
                                                    method ->
                                                            actual.add(method.name() + " " + path));
                            }
                        });
        Set<String> expected = new TreeSet<>();
        Map<String, ApiOperation> registry = new HashMap<>();
        for (ApiOperation operation : contract.operations()) {
            if (!operation.pathTemplate().startsWith("/api/v1/actuator/"))
                expected.add(operation.method() + " " + operation.pathTemplate());
            registry.put(operation.operationId(), operation);
        }
        var persisted = repository.operations("rbac-center", null, 1, registry.size());
        DomainException.require(
                persisted.total() == registry.size(), 503, "API_REGISTRY_CONTRACT_MISMATCH");
        for (ApiOperation operation : persisted.items()) {
            DomainException.require(
                    operation.matchesDefinition(registry.get(operation.operationId())),
                    503,
                    "API_REGISTRY_CONTRACT_MISMATCH");
        }
        DomainException.require(actual.equals(expected), 503, "API_ROUTE_CONTRACT_MISMATCH");
    }
}
