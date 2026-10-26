package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplacePermissionSetRequest(
        @NotNull Long version,
        @NotNull List<Long> permissionIds) {
}
