-- Owner: iam-audit-service / iam_audit
-- MySQL 8.4 LTS; connection timezone MUST be UTC.

CREATE TABLE iam_audit_log (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    actor_id        BIGINT UNSIGNED NOT NULL,
    action          VARCHAR(128) NOT NULL,
    resource_type   VARCHAR(128) NOT NULL,
    resource_id     BIGINT UNSIGNED NOT NULL,
    detail          JSON NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_tenant_time (tenant_id, created_at, id),
    KEY idx_audit_actor (tenant_id, actor_id, created_at, id),
    KEY idx_audit_action (tenant_id, action, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
