package com.enterprise.iam.identity.interfaces.http.dto;

public record PageOperationDto(
        String controlId,
        String operationId,
        String title,
        String actionType) {
}
