package com.enterprise.iam.identity.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDictDataRequest(
        @NotBlank String dictLabel,
        @NotBlank String dictValue,
        Integer sortOrder,
        String status,
        String cssTag,
        Boolean isDefault,
        String description,
        @NotNull Long version
) {}
