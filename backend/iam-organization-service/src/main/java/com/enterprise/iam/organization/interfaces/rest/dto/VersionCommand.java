package com.enterprise.iam.organization.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

public record VersionCommand(@NotNull Long version) {
}
