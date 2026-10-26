package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceDataScopesRequest(
        @NotNull Long bindingVersion,
        @NotNull @Size(max = 100) List<@Valid DataScopeBinding> bindings) {
}
