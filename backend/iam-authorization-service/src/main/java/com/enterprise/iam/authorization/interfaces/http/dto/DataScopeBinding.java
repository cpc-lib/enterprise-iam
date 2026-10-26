package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DataScopeBinding(
        @NotNull Long dataScopeId,
        @NotNull String mergeMode,
        @NotNull List<Long> teamIds,
        boolean includeChildren) {
}
