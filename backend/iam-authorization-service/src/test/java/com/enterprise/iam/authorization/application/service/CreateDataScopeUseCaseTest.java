package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.DataScopeRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateDataScopeUseCaseTest {

    @Test
    void createsDataScopeWithActiveStatus() {
        StubDataScopeRepository repo = new StubDataScopeRepository();
        CreateDataScopeUseCase useCase = new CreateDataScopeUseCase(repo);

        CreateDataScopeUseCase.Result result =
                useCase.create(1L, "FIN_ALL", "Finance All", "ALL", null);

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.version()).isZero();
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(repo.lastScopeCode).isEqualTo("FIN_ALL");
    }

    static class StubDataScopeRepository implements DataScopeRepository {
        String lastScopeCode;

        @Override
        public long create(long tenantId, String scopeCode, String scopeName, String scopeType, Long customPolicyId) {
            lastScopeCode = scopeCode;
            return 100L;
        }
    }
}
