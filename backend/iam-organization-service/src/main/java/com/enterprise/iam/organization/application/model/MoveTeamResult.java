package com.enterprise.iam.organization.application.model;

import com.enterprise.iam.organization.domain.model.TeamStatus;

public record MoveTeamResult(
        long teamId,
        TeamStatus status,
        long version) {
}
