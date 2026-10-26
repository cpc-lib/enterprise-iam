package com.enterprise.iam.organization.interfaces.rest.dto;

import java.time.Instant;

public record PositionResponse(
        long id,
        String positionCode,
        String positionName,
        Long organizationId,
        int sortOrder,
        String description,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {}
