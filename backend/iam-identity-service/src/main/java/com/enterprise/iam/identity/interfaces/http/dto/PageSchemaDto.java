package com.enterprise.iam.identity.interfaces.http.dto;

import java.util.List;

public record PageSchemaDto(
        String pageKey,
        String title,
        String resourceType,
        List<PageOperationDto> operations,
        List<PageColumnDto> columns) {
}
