package com.enterprise.iam.authorization.application.service;

import com.enterprise.iam.authorization.application.port.out.ResourceFieldRepository;
import com.enterprise.iam.authorization.application.port.out.ResourceFieldRepository.ResourceFieldInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplaceResourceFieldsUseCaseTest {

    @Test
    void replacesFieldsAndReturnsIncrementedVersion() {
        StubResourceFieldRepo repo = new StubResourceFieldRepo();
        ReplaceResourceFieldsUseCase useCase = new ReplaceResourceFieldsUseCase(repo);
        List<ResourceFieldInput> fields = List.of(
                new ResourceFieldInput("name", "name", "name", "STRING", "PUBLIC", false));

        ReplaceResourceFieldsUseCase.Result result = useCase.replace(1L, 5L, fields, 2L);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(repo.recordedFields).hasSize(1);
    }

    static class StubResourceFieldRepo implements ResourceFieldRepository {
        List<ResourceFieldInput> recordedFields;

        @Override
        public void replaceFields(long tenantId, long resourceId, List<ResourceFieldInput> fields, long expectedResourceVersion) {
            recordedFields = fields;
        }
    }
}
