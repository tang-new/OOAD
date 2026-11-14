package com.xmu.rbac.structured.dto;
import jakarta.validation.constraints.*;
/** 可复制的公开名称请求；不含持久化身份字段。 */
public class DisplayName {
    @NotBlank @Size(max = 128) private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
