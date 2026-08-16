-- Owner: iam-authorization-service / iam_authorization

CREATE TABLE iam_application (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    app_code        VARCHAR(64) NOT NULL,
    app_name        VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_code (tenant_id, app_code, delete_marker),
    CONSTRAINT ck_application_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_service (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    application_id  BIGINT UNSIGNED NOT NULL,
    service_code    VARCHAR(64) NOT NULL,
    service_name    VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_code (tenant_id, application_id, service_code, delete_marker),
    KEY idx_service_application (tenant_id, application_id, status),
    CONSTRAINT ck_service_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_resource (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    application_id  BIGINT UNSIGNED NOT NULL,
    service_id      BIGINT UNSIGNED NOT NULL,
    resource_code   VARCHAR(128) NOT NULL,
    resource_name   VARCHAR(128) NOT NULL,
    resource_type   VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    sharing_enabled TINYINT(1) NOT NULL DEFAULT 0,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_code (tenant_id, service_id, resource_code, delete_marker),
    KEY idx_resource_tenant_status (tenant_id, status, id),
    CONSTRAINT ck_resource_type CHECK (resource_type IN ('BUSINESS','MENU','PAGE','BUTTON','DATASET')),
    CONSTRAINT ck_resource_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_operation (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    operation_code  VARCHAR(128) NOT NULL,
    operation_name  VARCHAR(128) NOT NULL,
    risk_level      VARCHAR(16) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_code (tenant_id, operation_code, delete_marker),
    KEY idx_operation_tenant_status (tenant_id, status, id),
    CONSTRAINT ck_operation_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_operation_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_permission (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    resource_id     BIGINT UNSIGNED NOT NULL,
    operation_id    BIGINT UNSIGNED NOT NULL,
    status          VARCHAR(32) NOT NULL,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_resource_operation (tenant_id, resource_id, operation_id),
    KEY idx_permission_tenant_status (tenant_id, status, id),
    CONSTRAINT ck_permission_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_resource_operation (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    resource_id     BIGINT UNSIGNED NOT NULL,
    operation_id    BIGINT UNSIGNED NOT NULL,
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_operation (tenant_id, resource_id, operation_id),
    KEY idx_resource_operation_enabled (tenant_id, resource_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role_permission (
    id                  BIGINT UNSIGNED NOT NULL,
    tenant_id           BIGINT UNSIGNED NOT NULL,
    role_id             BIGINT UNSIGNED NOT NULL COMMENT 'Identity-owned ID; no cross-database FK',
    permission_id       BIGINT UNSIGNED NOT NULL,
    effect              VARCHAR(16) NOT NULL,
    priority            INT NOT NULL DEFAULT 0,
    condition_policy_id BIGINT UNSIGNED NULL,
    status              VARCHAR(32) NOT NULL,
    active_slot         TINYINT UNSIGNED NULL COMMENT '1 for active; NULL after revoke',
    start_time          DATETIME(3) NOT NULL,
    expire_time         DATETIME(3) NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission_active (tenant_id, role_id, permission_id, active_slot),
    KEY idx_role_permission_role (tenant_id, role_id, status, expire_time),
    KEY idx_role_permission_permission (tenant_id, permission_id, status),
    CONSTRAINT ck_role_permission_effect CHECK (effect IN ('ALLOW','DENY')),
    CONSTRAINT ck_role_permission_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_role_permission_slot CHECK (active_slot IS NULL OR active_slot = 1),
    CONSTRAINT ck_role_permission_time CHECK (expire_time IS NULL OR expire_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_permission_version (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    scope_type      VARCHAR(16) NOT NULL,
    subject_id      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0 for TENANT scope',
    version_value   BIGINT UNSIGNED NOT NULL,
    updated_reason  VARCHAR(64) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_version_scope (tenant_id, scope_type, subject_id),
    CONSTRAINT ck_permission_version_scope CHECK (
        (scope_type = 'TENANT' AND subject_id = 0) OR
        (scope_type = 'SUBJECT' AND subject_id > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
    KEY idx_outbox_aggregate (tenant_id, aggregate_type, aggregate_id, aggregate_version),
    CONSTRAINT ck_outbox_status CHECK (event_status IN ('PENDING','CLAIMED','PUBLISHED','DEAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
