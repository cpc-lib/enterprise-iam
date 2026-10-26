package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchAuthorizationCheckRequest(
        @NotEmpty
        @Size(max = 100)
        List<@Valid AuthorizationCheckRequest> checks) {
}
