package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.identity.domain.model.IdentityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantBootstrapRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{2,63}$") String tenantCode,
        @NotBlank @Size(max = 128) String tenantName,
        @NotNull InitialAdmin initialAdmin) {

    public record InitialAdmin(
            @NotNull IdentityType identityType,
            @NotBlank @Size(max = 320) String identity,
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank @Size(min = 15, max = 128) String initialPassword) {
    }
}
