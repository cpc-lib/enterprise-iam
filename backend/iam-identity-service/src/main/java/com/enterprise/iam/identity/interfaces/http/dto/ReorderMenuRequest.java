package com.enterprise.iam.identity.interfaces.http.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record ReorderMenuRequest(
        @NotEmpty Map<Long, Integer> menuOrderMap
) {}
