-- Owner: iam-sharing-service / iam_sharing

CREATE TABLE iam_resource_share (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL, resource_instance_key VARCHAR(128) NOT NULL,
    target_type VARCHAR(16) NOT NULL, target_id BIGINT UNSIGNED NOT NULL,
    creator_user_id BIGINT UNSIGNED NOT NULL, parent_share_id BIGINT UNSIGNED NULL,
    root_share_id BIGINT UNSIGNED NOT NULL, share_depth SMALLINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL, start_time DATETIME(3) NOT NULL,
    expire_time DATETIME(3) NOT NULL, can_reshare TINYINT(1) NOT NULL,
    active_slot TINYINT UNSIGNED NULL, revoke_reason VARCHAR(500) NULL,
    revoked_by BIGINT UNSIGNED NULL, revoked_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_active (tenant_id, resource_id, resource_instance_key, target_type, target_id, active_slot),
    KEY idx_share_target (tenant_id, target_type, target_id, status, expire_time),
    KEY idx_share_parent (tenant_id, parent_share_id, status),
    KEY idx_share_expiry (status, expire_time, id),
    CONSTRAINT ck_share_target CHECK (target_type IN ('USER','TEAM')),
    CONSTRAINT ck_share_status CHECK (status IN ('WAITING','ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_share_slot CHECK (active_slot IS NULL OR active_slot = 1),
    CONSTRAINT ck_share_time CHECK (expire_time > start_time),
    CONSTRAINT ck_share_depth CHECK (share_depth <= 8)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_resource_share_operation (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    share_id BIGINT UNSIGNED NOT NULL, operation_id BIGINT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_share_operation (tenant_id, share_id, operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_resource_share_field (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    share_id BIGINT UNSIGNED NOT NULL, operation_id BIGINT UNSIGNED NOT NULL,
    field_id BIGINT UNSIGNED NOT NULL, readable TINYINT(1) NOT NULL,
    writable TINYINT(1) NOT NULL, hidden TINYINT(1) NOT NULL,
    mask_strategy_id BIGINT UNSIGNED NULL, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_share_field (tenant_id, share_id, operation_id, field_id),
    CONSTRAINT ck_share_field_hidden CHECK (hidden = 0 OR (readable = 0 AND writable = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_resource_share_basis (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    share_id BIGINT UNSIGNED NOT NULL, basis_source_type VARCHAR(32) NOT NULL,
    basis_source_id BIGINT UNSIGNED NOT NULL, basis_permission_version BIGINT UNSIGNED NOT NULL,
    parent_share_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), KEY idx_share_basis (tenant_id, share_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_resource_share_history (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    share_id BIGINT UNSIGNED NOT NULL, action_type VARCHAR(32) NOT NULL,
    operator_type VARCHAR(16) NOT NULL, operator_id VARCHAR(128) NOT NULL,
    before_json JSON NULL, after_json JSON NULL, trace_id VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), KEY idx_share_history (tenant_id, share_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_resource_sharing_policy (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL, enabled TINYINT(1) NOT NULL,
    allowed_target_types JSON NOT NULL, max_share_duration_seconds BIGINT UNSIGNED NOT NULL,
    max_reshare_depth SMALLINT UNSIGNED NOT NULL, default_can_reshare TINYINT(1) NOT NULL,
    owner_transfer_policy VARCHAR(32) NOT NULL, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_sharing_policy (tenant_id, resource_id),
    CONSTRAINT ck_sharing_depth CHECK (max_reshare_depth <= 8),
    CONSTRAINT ck_owner_transfer_policy CHECK (owner_transfer_policy IN ('REVOKE_ALL','REVALIDATE','KEEP_IF_NEW_OWNER_ALLOWS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_share_projection_epoch (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL, epoch BIGINT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_share_epoch (tenant_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
