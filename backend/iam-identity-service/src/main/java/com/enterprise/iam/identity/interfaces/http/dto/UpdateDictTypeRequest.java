package com.enterprise.iam.identity.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDictTypeRequest(
        @NotBlank String dictName,
        String status,
        String description,
        @NotNull Long version
) {}
