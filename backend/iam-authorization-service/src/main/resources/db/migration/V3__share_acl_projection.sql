-- Owner: iam-authorization-service / iam_authorization
-- Spec 16: local read-model projection of share ACL grants delivered over MQ
-- plus the per-(tenant, resource) security epoch fence that fail-closes
-- authorization decisions while projection rows trail the sharing service.

CREATE TABLE iam_share_acl_projection (
    id                      BIGINT UNSIGNED NOT NULL,
    tenant_id               BIGINT UNSIGNED NOT NULL,
    share_id                BIGINT UNSIGNED NOT NULL,
    resource_id             BIGINT UNSIGNED NOT NULL,
    resource_instance_key   VARCHAR(128) NOT NULL,
    target_type             VARCHAR(16) NOT NULL,
    target_id               BIGINT UNSIGNED NOT NULL,
    operation_id            BIGINT UNSIGNED NOT NULL,
    start_time              DATETIME(3) NOT NULL,
    expire_time             DATETIME(3) NOT NULL,
    status                  VARCHAR(16) NOT NULL,
    projection_epoch        BIGINT UNSIGNED NOT NULL,
    version                 BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_acl_projection (
        tenant_id, share_id, operation_id, target_type, target_id),
    KEY idx_share_acl_decision (
        tenant_id, resource_id, resource_instance_key,
        target_type, target_id, status),
    CONSTRAINT ck_share_acl_target CHECK (target_type IN ('USER','TEAM')),
    CONSTRAINT ck_share_acl_status CHECK (status IN ('ACTIVE','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_share_security_epoch (
    tenant_id       BIGINT UNSIGNED NOT NULL,
    resource_id     BIGINT UNSIGNED NOT NULL,
    expected_epoch  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    applied_epoch   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, resource_id),
    CONSTRAINT ck_share_epoch_order CHECK (applied_epoch <= expected_epoch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
