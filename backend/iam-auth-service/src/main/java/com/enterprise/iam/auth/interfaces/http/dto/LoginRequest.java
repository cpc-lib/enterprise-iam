package com.enterprise.iam.auth.interfaces.http.dto;

import com.enterprise.iam.auth.domain.model.IdentityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{2,63}$") String tenantCode,
        @NotNull IdentityType identityType,
        @NotBlank @Size(max = 320) String identity,
        @NotBlank @Size(max = 128) String password,
        @Size(max = 128) String deviceName) {
}
