package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.application.port.out.ResourceFieldRepository;
import com.enterprise.iam.authorization.application.port.out.ResourceFieldRepository.ResourceFieldInput;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcResourceFieldRepository implements ResourceFieldRepository {

    private final AuthorizationPolicyMapper mapper;
    private final TransactionTemplate transaction;
    private final PositiveIdGenerator idGenerator;

    public JdbcResourceFieldRepository(
            AuthorizationPolicyMapper mapper,
            PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    @Override
    public void replaceFields(long tenantId, long resourceId, List<ResourceFieldInput> fields, long expectedResourceVersion) {
        transaction.executeWithoutResult(status -> {
            Instant now = Instant.now();
            mapper.disableActiveResourceFields(tenantId, resourceId, now);
            for (ResourceFieldInput field : fields) {
                long id = idGenerator.nextId();
                mapper.insertResourceField(
                        id, tenantId, resourceId, field.fieldCode(), field.propertyPath(),
                        field.columnName(), field.dataType(), field.sensitiveLevel(),
                        field.systemManaged(), now);
            }
        });
    }
}
