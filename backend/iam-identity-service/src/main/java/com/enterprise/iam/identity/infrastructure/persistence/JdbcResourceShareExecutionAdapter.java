package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.identity.application.port.out.ResourceShareExecutionPort;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class JdbcResourceShareExecutionAdapter implements ResourceShareExecutionPort {

    private final IdentityPersistenceMapper mapper;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public JdbcResourceShareExecutionAdapter(
            IdentityPersistenceMapper mapper,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void grantResourceShare(long tenantId, long userId, long resourceId, int durationHours) {
        Instant now = clock.instant();
        Instant expireTime = now.plus(Duration.ofHours(durationHours <= 0 ? 168 : durationHours));
        long aclId = idGenerator.nextId();
        mapper.insertShareAclApproval(
                aclId,
                tenantId,
                resourceId,
                "USER",
                userId,
                "READ",
                "ACTIVE",
                now,
                expireTime,
                now);
    }
}
