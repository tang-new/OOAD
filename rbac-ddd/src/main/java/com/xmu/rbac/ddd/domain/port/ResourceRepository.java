package com.xmu.rbac.ddd.domain.port;

import com.xmu.rbac.ddd.domain.*;

import java.util.*;

/** UI、API 独立资源与显式追踪映射的仓储。 */
public interface ResourceRepository {
    record Ui(
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
            String permissionCode) {}

    record Mapping(long uiResourceId, long apiOperationId) {}

    List<Ui> ui(String system);

    Ui ui(long id);

    Ui saveUi(Ui resource);

    void deleteUi(long id);

    AuthorizationRepository.Page<ApiOperation> operations(
            String system, String keyword, int page, int size);

    ApiOperation operation(long id);

    ApiOperation saveOperation(ApiOperation operation);

    List<Mapping> mappings(String system);

    void mappings(String system, List<Mapping> mappings);

    AuthorizationRepository.Page<Permission> permissions(
            String system, String kind, String keyword, int page, int size);

    Permission permission(long id);

    Permission savePermission(Permission permission);
}
