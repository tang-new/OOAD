package com.xmu.rbac.structured.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用于重命名的公开输入，只允许提交名称。 */
public record DisplayName(
        @NotBlank
        @Size(max = 128)
        String name) {
}
