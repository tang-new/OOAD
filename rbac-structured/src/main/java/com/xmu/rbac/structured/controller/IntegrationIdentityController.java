package com.xmu.rbac.structured.controller;

import com.xmu.rbac.structured.dto.Actor;
import com.xmu.rbac.structured.dto.IntegrationViews.AuthoritiesVO;
import com.xmu.rbac.structured.dto.IntegrationViews.IdentityUserVO;
import com.xmu.rbac.structured.service.IntegrationIdentityService;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 可信服务读取边界；公开前端代理不得暴露这些私有提供方接口。 */
@RestController
@Validated
public class IntegrationIdentityController {
    private final IntegrationIdentityService identities;

    public IntegrationIdentityController(IntegrationIdentityService identities) {
        this.identities = identities;
    }

    /** 返回当前令牌aud下的服务器计算能力，不接受客户端指定其他主体。 */
    @GetMapping("/integration/authorities")
    public AuthoritiesVO authorities(@RequestAttribute("actor") Actor actor,
            @RequestHeader(name = "X-Audit-Service-Key", required = false) String key) {
        return identities.authorities(actor, key);
    }

    /** 接入教师已有的稳定subject姓名工号查询。 */
    @GetMapping("/identity/users/{userId}")
    public IdentityUserVO lookup(@RequestAttribute("actor") Actor actor,
            @RequestHeader(name = "X-Audit-Service-Key", required = false) String key,
            @PathVariable @Size(min = 1, max = 128) String userId) {
        return identities.lookup(actor, key, userId);
    }
}
