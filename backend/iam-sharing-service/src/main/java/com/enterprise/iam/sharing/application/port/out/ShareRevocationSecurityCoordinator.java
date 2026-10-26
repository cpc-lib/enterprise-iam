package com.enterprise.iam.sharing.application.port.out;

/**
 * Coordinates the security fence update across boundaries upon share revoke/reduction.
 * In V1 baseline, outbox-only asynchronous propagation is sufficient (SPEC 47 §2).
 * Under V1.2 optional enhancement (SPEC 47 §2, SPEC 36 §10), a selective Seata transaction
 * synchronously coordinates advancing expected_epoch in the Authorization DB before reporting success.
 */
public interface ShareRevocationSecurityCoordinator {

    /**
     * Coordinate the security epoch advance with the authorization boundary.
     *
     * @param tenantId tenant identifier
     * @param resourceId shared resource identifier
     * @param nextEpoch the advancing epoch generated for this mutation
     */
    void coordinateRevocation(long tenantId, long resourceId, long nextEpoch);
}
