package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.FieldPolicyRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateFieldPolicyUseCaseTest {

    @Test
    void createsFieldPolicyWithActiveStatus() {
        StubFieldPolicyRepo repo = new StubFieldPolicyRepo();
        CreateFieldPolicyUseCase useCase = new CreateFieldPolicyUseCase(repo);

        CreateFieldPolicyUseCase.Result result = useCase.create(
                1L, 2L, 3L, 4L, true, false, false, null, 0);

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.version()).isZero();
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsHiddenPolicyWithReadWrite() {
        CreateFieldPolicyUseCase useCase = new CreateFieldPolicyUseCase(new StubFieldPolicyRepo());

        assertThatThrownBy(() -> useCase.create(1L, 2L, 3L, 4L, true, false, true, null, 0))
                .isInstanceOf(DomainInvariantException.class);
    }

    static class StubFieldPolicyRepo implements FieldPolicyRepository {
        @Override
        public long create(long tenantId, long resourceId, long operationId, long fieldId,
                           boolean readable, boolean writable, boolean hidden,
                           Long maskStrategyId, int priority) {
            return 200L;
        }
    }
}
