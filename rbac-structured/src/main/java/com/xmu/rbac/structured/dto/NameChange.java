package com.xmu.rbac.structured.dto;

import cn.edu.xmu.clonefactory.CopyFrom;

/** 重命名命令从受信上下文保留资源和操作者标识。 */
@CopyFrom(DisplayName.class)
public record NameChange(
        String name,
        @CopyFrom.Exclude(DisplayName.class)
        Long id,
        @CopyFrom.Exclude(DisplayName.class)
        Long actorId) {
}
