package com.enterprise.iam.identity.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldDefinitionDto(
        String fieldKey,
        String fieldName,
        String dataType,
        boolean sensitive,
        boolean maskable,
        String description) {
}
