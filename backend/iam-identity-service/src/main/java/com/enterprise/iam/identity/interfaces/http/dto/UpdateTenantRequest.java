package com.enterprise.iam.identity.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank(message = "租户名称不能为空")
        @Size(max = 128, message = "租户名称长度不能超过128个字符")
        String tenantName) {
}
