package com.enterprise.iam.organization.application.model;

import com.enterprise.iam.organization.domain.model.TeamStatus;

public record CreateTeamResult(
        long teamId,
        TeamStatus status,
        long version) {
}
