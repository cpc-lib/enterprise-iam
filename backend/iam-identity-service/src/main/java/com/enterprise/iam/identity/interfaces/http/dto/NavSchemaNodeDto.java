package com.enterprise.iam.identity.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NavSchemaNodeDto(
        String key,
        String title,
        String path,
        String icon,
        Integer order,
        List<NavItemDto> items) {
}
