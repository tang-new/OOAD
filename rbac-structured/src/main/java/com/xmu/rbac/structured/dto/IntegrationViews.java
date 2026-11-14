package com.xmu.rbac.structured.dto;

import java.util.List;

/** 仅供受信课程后端读取的白名单输出，不包含凭据和持久化记录。 */
public final class IntegrationViews {
    private IntegrationViews() { }

    /** 当前目标系统的真实API能力及受控身份投影。 */
    public record AuthoritiesVO(List<String> authorities) { }

    /** 教师系统既有统一身份查询协议。 */
    public record IdentityUserVO(String userId, String name, String no) { }
}
