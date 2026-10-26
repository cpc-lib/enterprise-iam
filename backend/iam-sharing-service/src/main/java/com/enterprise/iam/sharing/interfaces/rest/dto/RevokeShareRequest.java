package com.enterprise.iam.sharing.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RevokeShareRequest(
        @NotNull Long version,
        @NotBlank @Size(min = 1, max = 500) String reason) {
}
