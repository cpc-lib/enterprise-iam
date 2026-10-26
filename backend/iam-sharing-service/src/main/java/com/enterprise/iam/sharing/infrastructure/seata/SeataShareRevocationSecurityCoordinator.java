package com.enterprise.iam.sharing.infrastructure.seata;

import com.enterprise.iam.common.transaction.SeataRevocationCoordinationException;
import com.enterprise.iam.common.transaction.SeataTransactionProperties;
import com.enterprise.iam.common.transaction.SeataTransactionSupport;
import com.enterprise.iam.sharing.application.port.out.ShareRevocationSecurityCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * V1.2 Seata optional enhancement implementation (SPEC 47 §2, SPEC 36 §10).
 * Atomically coordinates the revoke / reduction in Sharing DB with advancing
 * {@code iam_share_security_epoch.expected_epoch} in Authorization DB, immediately
 * activating the authorization fail-closed fence before reporting success.
 */
public final class SeataShareRevocationSecurityCoordinator implements ShareRevocationSecurityCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SeataShareRevocationSecurityCoordinator.class);

    private final AuthorizationSecurityEpochSynchronizer epochSynchronizer;
    private final SeataTransactionProperties properties;
    private final Clock clock;

    public SeataShareRevocationSecurityCoordinator(
            AuthorizationSecurityEpochSynchronizer epochSynchronizer,
            SeataTransactionProperties properties,
            Clock clock) {
        this.epochSynchronizer = Objects.requireNonNull(epochSynchronizer, "epochSynchronizer must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void coordinateRevocation(long tenantId, long resourceId, long nextEpoch) {
        if (!properties.enabled()) {
            log.debug("Seata coordination disabled by properties, skipping synchronization");
            return;
        }

        if (properties.requireGlobalTx() && !SeataTransactionSupport.inGlobalTransaction()) {
            throw new IllegalStateException(
                    "Seata global transaction is required by configuration but no active global transaction context was detected");
        }

        String xid = SeataTransactionSupport.getXid();
        log.info("Coordinating share revocation across DBs via Seata [xid={}, tenantId={}, resourceId={}, epoch={}]",
                xid, tenantId, resourceId, nextEpoch);

        try {
            Instant now = clock.instant();
            epochSynchronizer.advanceExpectedEpoch(tenantId, resourceId, nextEpoch, now);
            log.debug("Successfully synchronized authorization expected_epoch to {} for tenant={}, resource={}",
                    nextEpoch, tenantId, resourceId);
        } catch (Exception e) {
            log.error("Failed to coordinate authorization security epoch via Seata for tenant={}, resource={}, epoch={}: {}",
                    tenantId, resourceId, nextEpoch, e.getMessage(), e);
            throw new SeataRevocationCoordinationException(
                    "Failed to atomically advance authorization expected_epoch under Seata transaction: " + e.getMessage(), e);
        }
    }
}
