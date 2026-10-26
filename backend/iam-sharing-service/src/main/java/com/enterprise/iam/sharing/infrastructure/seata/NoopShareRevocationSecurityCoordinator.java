package com.enterprise.iam.sharing.infrastructure.seata;

import com.enterprise.iam.sharing.application.port.out.ShareRevocationSecurityCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default V1 baseline implementation: outbox-only asynchronous propagation.
 * Relies on the monotonic epoch carried in the Outbox/MQ event and the authorization-side
 * fail-closed epoch fence (SPEC 47 §2).
 */
public final class NoopShareRevocationSecurityCoordinator implements ShareRevocationSecurityCoordinator {

    private static final Logger log = LoggerFactory.getLogger(NoopShareRevocationSecurityCoordinator.class);

    @Override
    public void coordinateRevocation(long tenantId, long resourceId, long nextEpoch) {
        log.debug("Seata optional enhancement disabled; relying on outbox/MQ epoch fence for tenant={}, resource={}, epoch={}",
                tenantId, resourceId, nextEpoch);
    }
}
