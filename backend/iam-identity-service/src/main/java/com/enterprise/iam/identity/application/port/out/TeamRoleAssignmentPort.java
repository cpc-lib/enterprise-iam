package com.enterprise.iam.identity.application.port.out;

public interface TeamRoleAssignmentPort {

    void assignTeamRole(long tenantId, long userId, long teamRoleId, int durationHours);
}
