package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.TeamRoleAssignmentPort;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class JdbcTeamRoleAssignmentAdapter implements TeamRoleAssignmentPort {

    private final IdentityPersistenceMapper mapper;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public JdbcTeamRoleAssignmentAdapter(
            IdentityPersistenceMapper mapper,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void assignTeamRole(long tenantId, long userId, long teamRoleId, int durationHours) {
        Long teamId = mapper.findTeamIdByTeamRole(tenantId, teamRoleId);
        if (teamId == null) {
            teamId = 1L; // fallback to root team
        }
        Instant now = clock.instant();
        Instant expireTime = now.plus(Duration.ofHours(durationHours <= 0 ? 168 : durationHours));
        long id = idGenerator.nextId();
        mapper.insertTeamMemberRoleApproval(id, tenantId, teamId, userId, teamRoleId, now, expireTime, now);
    }
}
