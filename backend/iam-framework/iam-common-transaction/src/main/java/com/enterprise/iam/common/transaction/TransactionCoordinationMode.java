package com.enterprise.iam.common.transaction;

/**
 * Transaction coordination modes defined in SPEC 24 §64 and SPEC 47 §2.
 * Priority: Local TX > Outbox > MQ > Saga > Seata.
 */
public enum TransactionCoordinationMode {
    /**
     * V1 baseline: local transaction with asynchronous Outbox/MQ propagation and epoch fence.
     */
    LOCAL,

    /**
     * V1.2 optional enhancement: short selective Seata transaction for security-critical
     * cross-DB atomicity (e.g. share revoke/reduction).
     */
    SEATA
}
