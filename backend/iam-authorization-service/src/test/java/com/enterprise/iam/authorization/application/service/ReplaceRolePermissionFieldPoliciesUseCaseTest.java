package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionFieldPolicyRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplaceRolePermissionFieldPoliciesUseCaseTest {

    @Test
    void replacesFieldPoliciesAndAdvancesVersion() {
        StubFieldPolicyRepo repo = new StubFieldPolicyRepo();
        StubVersionRepo versionRepo = new StubVersionRepo();
        ReplaceRolePermissionFieldPoliciesUseCase useCase =
                new ReplaceRolePermissionFieldPoliciesUseCase(repo, versionRepo);

        ReplaceRolePermissionFieldPoliciesUseCase.Result result =
                useCase.replace(1L, 5L, List.of(10L, 20L), 3L);

        assertThat(result.bindingVersion()).isEqualTo(4L);
        assertThat(result.permissionVersion()).isEqualTo(1L);
        assertThat(versionRepo.lastReason).isEqualTo("ROLE_PERMISSION_FIELD_POLICY_REPLACE");
        assertThat(repo.recorded).containsExactly(10L, 20L);
    }

    static class StubFieldPolicyRepo implements RolePermissionFieldPolicyRepository {
        List<Long> recorded = new ArrayList<>();

        @Override
        public void replaceFieldPolicies(long tenantId, long rolePermissionId, List<Long> fieldPolicyIds, long expectedBindingVersion) {
            recorded = new ArrayList<>(fieldPolicyIds);
        }
    }

    static class StubVersionRepo implements PermissionVersionRepository {
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
