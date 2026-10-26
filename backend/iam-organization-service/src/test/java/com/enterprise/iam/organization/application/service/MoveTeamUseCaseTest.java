package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.command.MoveTeamCommand;
import com.enterprise.iam.organization.application.model.MoveTeamResult;
import com.enterprise.iam.organization.application.port.out.TeamRepository;
import com.enterprise.iam.organization.domain.model.Team;
import com.enterprise.iam.organization.domain.model.TeamStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoveTeamUseCaseTest {

    private final StubTeamRepository repository = new StubTeamRepository();
    private final MoveTeamUseCase useCase = new MoveTeamUseCase(repository);

    @Test
    void movesTeamUnderNewParentAndAdvancesVersion() {
        Team rootA = team(10L, null, 0, "/10/");
        Team rootB = team(20L, null, 0, "/20/");
        repository.save(rootA);
        repository.save(rootB);

        MoveTeamResult result = useCase.move(new MoveTeamCommand(1L, 10L, 20L, 0L, "trace-1"));

        assertThat(result.teamId()).isEqualTo(10L);
        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(TeamStatus.ACTIVE);
        assertThat(repository.findById(1L, 10L).orElseThrow().parentTeamId()).isEqualTo(20L);
    }

    @Test
    void rejectsMoveThatWouldCreateCycle() {
        Team rootA = team(10L, null, 0, "/10/");
        Team rootB = team(20L, null, 0, "/20/");
        repository.save(rootA);
        repository.save(rootB);
        useCase.move(new MoveTeamCommand(1L, 10L, 20L, 0L, "trace-1"));

        assertThatThrownBy(() -> useCase.move(new MoveTeamCommand(1L, 20L, 10L, 0L, "trace-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }

    private static Team team(long id, Long parentTeamId, int depth, String path) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new Team(
                id, 1L, 1L, parentTeamId, "CODE" + id, "Name " + id, path, depth,
                TeamStatus.ACTIVE, 0L, now, now);
    }

    static final class StubTeamRepository implements TeamRepository {
        private final Map<Long, Team> byId = new HashMap<>();

        @Override
        public Team save(Team team) {
            byId.put(team.id(), team);
            return team;
        }

        @Override
        public Optional<Team> findById(long tenantId, long teamId) {
            return Optional.ofNullable(byId.get(teamId));
        }

        @Override
        public Optional<Team> findByCode(long tenantId, String teamCode) {
            return byId.values().stream().filter(t -> t.teamCode().equals(teamCode)).findFirst();
        }

        @Override
        public void moveTeam(long tenantId, long teamId, Long newParentTeamId, long expectedVersion) {
            Team existing = byId.get(teamId);
            if (existing == null || existing.version() != expectedVersion) {
                throw new IllegalStateException("team move conflict");
            }
            Instant now = Instant.parse("2026-01-02T00:00:00Z");
            byId.put(teamId, new Team(
                    existing.id(), existing.tenantId(), existing.organizationId(), newParentTeamId,
                    existing.teamCode(), existing.teamName(), existing.materializedPath(),
                    existing.depth(), existing.status(), existing.version() + 1,
                    existing.createdAt(), now));
        }
    }
}
