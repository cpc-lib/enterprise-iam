package com.enterprise.iam.sharing.infrastructure.seata;

import java.time.Instant;

/**
 * Port for advancing the expected epoch in the Authorization DB during a short selective
 * Seata transaction.
 */
public interface AuthorizationSecurityEpochSynchronizer {

    /**
     * Advance the expected security epoch for (tenantId, resourceId) in the authorization store.
     *
     * @param tenantId tenant identifier
     * @param resourceId shared resource identifier
     * @param epoch the advancing epoch
     * @param now timestamp of synchronization
     */
    void advanceExpectedEpoch(long tenantId, long resourceId, long epoch, Instant now);
}
