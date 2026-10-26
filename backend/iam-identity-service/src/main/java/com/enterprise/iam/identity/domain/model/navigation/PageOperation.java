package com.enterprise.iam.identity.domain.model.navigation;

public record PageOperation(
        String controlId,
        String operationId,
        String title,
        String actionType) {
}
