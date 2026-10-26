package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ResourceField(
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$") String fieldCode,
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){0,7}$") String propertyPath,
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$") String columnName,
        @NotNull String dataType,
        @NotNull String sensitiveLevel,
        boolean systemManaged) {
}
