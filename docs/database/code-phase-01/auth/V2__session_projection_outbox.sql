-- Owner: iam-auth-service / iam_auth
-- Durable session projection event written in the same transaction as session state.

CREATE TABLE sys_outbox_event (
    id                  BIGINT UNSIGNED NOT NULL,
    event_id            BIGINT UNSIGNED NOT NULL,
    tenant_id           BIGINT UNSIGNED NOT NULL,
    aggregate_type      VARCHAR(64) NOT NULL,
    aggregate_id        BIGINT UNSIGNED NOT NULL,
    aggregate_version   BIGINT UNSIGNED NOT NULL,
    event_type          VARCHAR(128) NOT NULL,
    schema_version      INT UNSIGNED NOT NULL,
    exchange_name       VARCHAR(128) NOT NULL,
    routing_key         VARCHAR(128) NOT NULL,
    payload             JSON NOT NULL,
    event_status        VARCHAR(32) NOT NULL,
    retry_count         INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at       DATETIME(3) NULL,
    claim_owner         VARCHAR(128) NULL,
    claim_until         DATETIME(3) NULL,
    last_error_code     VARCHAR(128) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at        DATETIME(3) NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event (event_id),
    KEY idx_outbox_relay (event_status, next_retry_at, id),
    KEY idx_outbox_claim (event_status, claim_until, id),
    KEY idx_outbox_aggregate (
        tenant_id, aggregate_type, aggregate_id, aggregate_version),
    CONSTRAINT ck_outbox_status
        CHECK (event_status IN ('PENDING','CLAIMED','PUBLISHED','DEAD')),
    CONSTRAINT ck_outbox_claim_pair CHECK (
        (event_status = 'CLAIMED' AND claim_owner IS NOT NULL AND claim_until IS NOT NULL)
        OR
        (event_status <> 'CLAIMED' AND claim_owner IS NULL AND claim_until IS NULL)
    ),
    CONSTRAINT ck_outbox_published_at CHECK (
        (event_status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR
        (event_status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    CONSTRAINT ck_outbox_retry_count CHECK (retry_count <= 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
