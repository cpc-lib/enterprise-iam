package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceResourceFieldsRequest(
        @NotNull Long resourceVersion,
        @NotNull @Size(max = 1000) List<@Valid ResourceField> fields) {
}
