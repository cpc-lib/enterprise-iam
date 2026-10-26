package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceFieldPoliciesRequest(
        @NotNull Long bindingVersion,
        @NotNull List<Long> fieldPolicyIds) {
}
