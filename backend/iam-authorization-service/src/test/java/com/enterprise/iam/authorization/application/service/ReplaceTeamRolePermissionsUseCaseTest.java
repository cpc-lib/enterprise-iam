package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.TeamRolePermissionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplaceTeamRolePermissionsUseCaseTest {

    @Test
    void replacesPermissionsAndAdvancesVersion() {
        StubTeamRoleRepository teamRepo = new StubTeamRoleRepository();
        StubVersionRepository versionRepo = new StubVersionRepository();
        ReplaceTeamRolePermissionsUseCase useCase =
                new ReplaceTeamRolePermissionsUseCase(teamRepo, versionRepo);

        ReplaceTeamRolePermissionsUseCase.Result result =
                useCase.replace(1L, 2L, List.of(10L, 20L), 3L);

        assertThat(result.bindingVersion()).isEqualTo(4L);
        assertThat(result.permissionVersion()).isEqualTo(1L);
        assertThat(teamRepo.recorded).containsExactly(10L, 20L);
        assertThat(versionRepo.lastReason).isEqualTo("TEAM_ROLE_PERMISSION_REPLACE");
    }

    static class StubTeamRoleRepository implements TeamRolePermissionRepository {
        List<Long> recorded = new ArrayList<>();

        @Override
        public void replacePermissions(long tenantId, long teamRoleId, List<Long> permissionIds, long expectedVersion) {
            recorded = new ArrayList<>(permissionIds);
        }

        @Override
        public List<Long> findActivePermissionIds(long tenantId, long teamRoleId) {
            return recorded;
        }
    }

    static class StubVersionRepository implements PermissionVersionRepository {
        long version = 0;
        String lastReason;

        @Override
        public long getTenantPermissionVersion(long tenantId) {
            return version;
        }

        @Override
        public long incrementTenantPermissionVersion(long tenantId, String reason) {
            lastReason = reason;
            return ++version;
        }
    }
}
