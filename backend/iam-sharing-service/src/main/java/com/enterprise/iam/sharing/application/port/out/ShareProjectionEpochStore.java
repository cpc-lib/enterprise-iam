package com.enterprise.iam.sharing.application.port.out;

/**
 * Monotonic per-(tenant, resource) projection epoch fence. A revoke inserts
 * the fence at {@code 2} (the granted event always carries epoch 1) and every
 * later revoke increments by one. Consumers fail closed while their applied
 * epoch trails the expected epoch.
 */
public interface ShareProjectionEpochStore {

    long nextEpoch(long tenantId, long resourceId);
}
