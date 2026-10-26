package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.command.RemoveTeamMemberCommand;
import com.enterprise.iam.organization.application.port.out.TeamMemberRepository;
import com.enterprise.iam.organization.domain.model.TeamMember;
import com.enterprise.iam.organization.domain.model.TeamMemberStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RemoveTeamMemberUseCaseTest {

    private final StubTeamMemberRepository repository = new StubTeamMemberRepository();
    private final RemoveTeamMemberUseCase useCase = new RemoveTeamMemberUseCase(repository);

    @Test
    void revokesActiveMembershipByVersion() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        repository.save(new TeamMember(
                300L, 1L, 50L, 7L, TeamMemberStatus.ACTIVE, now, null, 0L));
        assertThat(repository.findActive(1L, 50L, 7L)).isPresent();

        useCase.remove(new RemoveTeamMemberCommand(1L, 50L, 7L, 0L, "trace-1"));

        assertThat(repository.findActive(1L, 50L, 7L)).isEmpty();
        assertThat(repository.removedVersion).isEqualTo(1L);
    }

    static final class StubTeamMemberRepository implements TeamMemberRepository {
        private final Map<Long, TeamMember> byUser = new HashMap<>();
        private long removedVersion = -1L;

        @Override
        public TeamMember save(TeamMember teamMember) {
            byUser.put(teamMember.userId(), teamMember);
            return teamMember;
        }

        @Override
        public Optional<TeamMember> findActive(long tenantId, long teamId, long userId) {
            TeamMember member = byUser.get(userId);
            return member != null && member.membershipStatus() == TeamMemberStatus.ACTIVE
                    ? Optional.of(member) : Optional.empty();
        }

        @Override
        public void removeMember(long tenantId, long teamId, long userId, long expectedVersion) {
            TeamMember existing = byUser.get(userId);
            if (existing == null
                    || existing.membershipStatus() != TeamMemberStatus.ACTIVE
                    || existing.version() != expectedVersion) {
                throw new IllegalStateException("team member remove conflict");
            }
            Instant leftAt = Instant.parse("2026-01-02T00:00:00Z");
            byUser.put(userId, new TeamMember(
                    existing.id(), existing.tenantId(), existing.teamId(), existing.userId(),
                    TeamMemberStatus.LEFT, existing.joinedAt(), leftAt, existing.version() + 1));
            removedVersion = existing.version() + 1;
        }
    }
}
