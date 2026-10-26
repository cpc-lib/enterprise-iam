package com.enterprise.iam.authorization.interfaces.http.dto;

import java.util.List;

public record BatchAuthorizationCheckResponse(
        List<AuthorizationCheckResponse> results) {
}
