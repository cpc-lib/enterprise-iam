package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.PermissionVersionRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionDataScopeRepository;
import com.enterprise.iam.authorization.application.port.out.RolePermissionDataScopeRepository.DataScopeBindingInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplaceRolePermissionDataScopesUseCaseTest {

    @Test
    void replacesDataScopesAndAdvancesPermissionVersion() {
        StubDataScopeRepo dataScopeRepo = new StubDataScopeRepo();
        StubVersionRepo versionRepo = new StubVersionRepo();
        ReplaceRolePermissionDataScopesUseCase useCase =
                new ReplaceRolePermissionDataScopesUseCase(dataScopeRepo, versionRepo);

        List<DataScopeBindingInput> bindings = List.of(
                new DataScopeBindingInput(10L, "UNION", List.of(1L, 2L), true));

        ReplaceRolePermissionDataScopesUseCase.Result result =
                useCase.replace(1L, 5L, bindings, 3L);

        assertThat(result.bindingVersion()).isEqualTo(4L);
        assertThat(result.permissionVersion()).isEqualTo(1L);
        assertThat(versionRepo.lastReason).isEqualTo("ROLE_PERMISSION_DATA_SCOPE_REPLACE");
        assertThat(dataScopeRepo.recordedBindings).hasSize(1);
    }

    static class StubDataScopeRepo implements RolePermissionDataScopeRepository {
        List<DataScopeBindingInput> recordedBindings;

        @Override
        public void replaceDataScopes(long tenantId, long rolePermissionId, List<DataScopeBindingInput> bindings, long expectedBindingVersion) {
            recordedBindings = bindings;
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
