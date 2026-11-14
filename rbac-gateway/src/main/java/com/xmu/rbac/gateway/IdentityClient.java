package com.xmu.rbac.gateway;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;

/** 中心鉴权使用声明式 HTTP；只传当前调用者的 bearer，保留状态供契约边界核验。 */
public interface IdentityClient {
    @GetExchange("/auth/api-permissions/{operationId}")
    ResponseEntity<Decision> check(@PathVariable("operationId") String operationId,
                   @RequestHeader("Authorization") String authorization);

    /** 中心返回的全局资格，不代表课程对象权限。 */
    record Decision(String operationId, Boolean allowed) { }
}
