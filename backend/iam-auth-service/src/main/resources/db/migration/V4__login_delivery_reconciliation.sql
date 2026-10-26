-- Owner: iam-auth-service / iam_auth
-- Durable acknowledgement and reconciliation state for ambiguous login responses.

CREATE TABLE iam_login_delivery (
    session_id          BIGINT UNSIGNED NOT NULL,
    tenant_id           BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    request_id          VARCHAR(128) NOT NULL COMMENT 'Correlation only; never a deduplication key',
    delivery_status     VARCHAR(32) NOT NULL,
    confirm_before      DATETIME(3) NOT NULL,
    confirmed_at        DATETIME(3) NULL,
    reconciled_at       DATETIME(3) NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (session_id),
    KEY idx_login_delivery_reconcile (delivery_status, confirm_before, session_id),
    KEY idx_login_delivery_user (tenant_id, user_id, created_at, session_id),
    CONSTRAINT ck_login_delivery_status
        CHECK (delivery_status IN ('PENDING','CONFIRMED','RECONCILED')),
    CONSTRAINT ck_login_delivery_terminal_time CHECK (
        (delivery_status = 'PENDING' AND confirmed_at IS NULL AND reconciled_at IS NULL)
        OR (delivery_status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND reconciled_at IS NULL)
        OR (delivery_status = 'RECONCILED' AND confirmed_at IS NULL AND reconciled_at IS NOT NULL)
    ),
    CONSTRAINT ck_login_delivery_deadline CHECK (confirm_before > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
