package com.enterprise.iam.auth.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;

public record ChangePasswordRequest(
        @NotNull String currentPassword,
        @NotNull String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=REDACTED,newPassword=REDACTED]";
    }
}
