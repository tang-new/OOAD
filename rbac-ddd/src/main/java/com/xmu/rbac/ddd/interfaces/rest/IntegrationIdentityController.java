package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.application.IntegrationIdentityApplicationService;
import com.xmu.rbac.ddd.application.IntegrationIdentityApplicationService.AuthoritiesResult;
import com.xmu.rbac.ddd.application.IntegrationIdentityApplicationService.IdentityResult;

import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 可信服务协议适配：与外部七接口相互独立，不向浏览器开放API权限清单。 */
@RestController
@Validated
public class IntegrationIdentityController {
    private final RestSupport rest;
    private final IntegrationIdentityApplicationService identities;

    public IntegrationIdentityController(
            RestSupport rest, IntegrationIdentityApplicationService identities) {
        this.rest = rest;
        this.identities = identities;
    }

    /** 返回当前用户/当前系统的服务器计算能力。 */
    @GetMapping("/integration/authorities")
    public AuthoritiesResult authorities(
            @RequestHeader(name = "Authorization", required = false) String bearer,
            @RequestHeader(name = "X-Audit-Service-Key", required = false) String key) {
        return identities.authorities(rest.principal(bearer), key);
    }

    /** 按教师原接口解析稳定subject，应用服务负责访问规则。 */
    @GetMapping("/identity/users/{userId}")
    public IdentityResult lookup(
            @RequestHeader(name = "Authorization", required = false) String bearer,
            @RequestHeader(name = "X-Audit-Service-Key", required = false) String key,
            @PathVariable @Size(min = 1, max = 128) String userId) {
        return identities.lookup(rest.principal(bearer), key, userId);
    }
}
