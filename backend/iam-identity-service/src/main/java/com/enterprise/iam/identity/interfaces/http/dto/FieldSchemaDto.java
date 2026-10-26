package com.enterprise.iam.identity.interfaces.http.dto;

import java.util.List;

public record FieldSchemaDto(
        String resourceType,
        List<FieldDefinitionDto> fields) {
}
