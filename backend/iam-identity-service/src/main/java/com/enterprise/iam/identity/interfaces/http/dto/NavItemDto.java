package com.enterprise.iam.identity.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NavItemDto(
        String key,
        String path,
        String title,
        String icon,
        Integer order) {
}
