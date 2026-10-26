package com.enterprise.iam.organization.application.model;

import com.enterprise.iam.organization.domain.model.OrganizationStatus;

public record CreateOrganizationResult(
        long organizationId,
        OrganizationStatus status,
        long version) {
}
