package com.enterprise.iam.sharing.infrastructure.seata;

import com.enterprise.iam.sharing.infrastructure.persistence.mapper.SharingPersistenceMapper;

import java.time.Instant;
import java.util.Objects;

/**
 * JDBC/MyBatis-Plus implementation advancing {@code iam_share_security_epoch.expected_epoch}
 * within the coordinated transaction branch.
 */
public final class JdbcAuthorizationSecurityEpochSynchronizer implements AuthorizationSecurityEpochSynchronizer {

    private final SharingPersistenceMapper mapper;

    public JdbcAuthorizationSecurityEpochSynchronizer(SharingPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public void advanceExpectedEpoch(long tenantId, long resourceId, long epoch, Instant now) {
        mapper.advanceExpectedEpoch(tenantId, resourceId, epoch, now);
    }
}
