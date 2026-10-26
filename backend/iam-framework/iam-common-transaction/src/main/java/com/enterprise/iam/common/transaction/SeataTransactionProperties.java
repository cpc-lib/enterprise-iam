package com.enterprise.iam.common.transaction;

/**
 * Standard configuration properties for Seata transaction boundaries across IAM services.
 */
public record SeataTransactionProperties(
        boolean enabled,
        String mode,
        boolean requireGlobalTx,
        String applicationId,
        String txServiceGroup) {

    public static final String DEFAULT_MODE = "AT";
    public static final String DEFAULT_TX_SERVICE_GROUP = "default_tx_group";

    public SeataTransactionProperties {
        if (mode == null || mode.isBlank()) {
            mode = DEFAULT_MODE;
        }
        if (txServiceGroup == null || txServiceGroup.isBlank()) {
            txServiceGroup = DEFAULT_TX_SERVICE_GROUP;
        }
    }

    public static SeataTransactionProperties disabled() {
        return new SeataTransactionProperties(false, DEFAULT_MODE, false, "iam-service", DEFAULT_TX_SERVICE_GROUP);
    }
}
