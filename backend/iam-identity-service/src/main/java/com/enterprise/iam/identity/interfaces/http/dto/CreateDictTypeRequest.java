package com.enterprise.iam.identity.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDictTypeRequest(
        @NotBlank String dictType,
        @NotBlank String dictName,
        String status,
        String description
) {}
