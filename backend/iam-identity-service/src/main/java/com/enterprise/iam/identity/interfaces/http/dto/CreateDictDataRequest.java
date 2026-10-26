package com.enterprise.iam.identity.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDictDataRequest(
        @NotBlank String dictType,
        @NotBlank String dictLabel,
        @NotBlank String dictValue,
        Integer sortOrder,
        String status,
        String cssTag,
        Boolean isDefault,
        String description
) {}
