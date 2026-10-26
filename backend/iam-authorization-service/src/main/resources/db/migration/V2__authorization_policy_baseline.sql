-- Owner: iam-authorization-service / iam_authorization

CREATE TABLE iam_team_role_permission (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    team_id BIGINT UNSIGNED NOT NULL, team_role_id BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL, effect VARCHAR(16) NOT NULL,
    priority INT NOT NULL DEFAULT 0, condition_policy_id BIGINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL, active_slot TINYINT UNSIGNED NULL,
    start_time DATETIME(3) NOT NULL, expire_time DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_role_permission_active (tenant_id, team_role_id, permission_id, active_slot),
    KEY idx_team_role_permission_lookup (tenant_id, team_id, team_role_id, status, expire_time),
    CONSTRAINT ck_team_role_permission_effect CHECK (effect IN ('ALLOW','DENY')),
    CONSTRAINT ck_team_role_permission_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_team_role_permission_slot CHECK (active_slot IS NULL OR active_slot = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_resource_data_schema (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL, service_id BIGINT UNSIGNED NOT NULL,
    datasource_key VARCHAR(64) NOT NULL, schema_name VARCHAR(64) NOT NULL,
    table_name VARCHAR(64) NOT NULL, primary_key_column VARCHAR(64) NOT NULL,
    owner_user_column VARCHAR(64) NULL, owner_team_column VARCHAR(64) NULL,
    tenant_column VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_resource_data_schema (tenant_id, resource_id),
    CONSTRAINT ck_data_schema_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_data_scope (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    scope_code VARCHAR(64) NOT NULL, scope_name VARCHAR(128) NOT NULL,
    scope_type VARCHAR(32) NOT NULL, custom_policy_id BIGINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL, delete_marker BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_data_scope_code (tenant_id, scope_code, delete_marker),
    CONSTRAINT ck_data_scope_type CHECK (scope_type IN ('ALL','SELF','TEAM','TEAM_AND_CHILDREN','SPECIFIED_TEAM','SHARED','CUSTOM_POLICY')),
    CONSTRAINT ck_data_scope_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role_data_scope (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    role_permission_id BIGINT UNSIGNED NOT NULL, data_scope_id BIGINT UNSIGNED NOT NULL,
    merge_mode VARCHAR(16) NOT NULL, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_role_data_scope (tenant_id, role_permission_id, data_scope_id),
    CONSTRAINT ck_role_scope_merge CHECK (merge_mode IN ('UNION','INTERSECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role_data_scope_team (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    role_data_scope_id BIGINT UNSIGNED NOT NULL, team_id BIGINT UNSIGNED NOT NULL,
    include_children TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_role_scope_team (tenant_id, role_data_scope_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_team_role_data_scope (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    team_role_permission_id BIGINT UNSIGNED NOT NULL, data_scope_id BIGINT UNSIGNED NOT NULL,
    merge_mode VARCHAR(16) NOT NULL, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_team_role_data_scope (tenant_id, team_role_permission_id, data_scope_id),
    CONSTRAINT ck_team_role_scope_merge CHECK (merge_mode IN ('UNION','INTERSECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_team_role_data_scope_team (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    team_role_data_scope_id BIGINT UNSIGNED NOT NULL, team_id BIGINT UNSIGNED NOT NULL,
    include_children TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_team_role_scope_team (tenant_id, team_role_data_scope_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_resource_field (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL, field_code VARCHAR(64) NOT NULL,
    property_path VARCHAR(512) NOT NULL, column_name VARCHAR(64) NULL,
    data_type VARCHAR(32) NOT NULL, sensitive_level VARCHAR(32) NOT NULL,
    system_managed TINYINT(1) NOT NULL, discovery_status VARCHAR(32) NOT NULL,
    default_mask_strategy_id BIGINT UNSIGNED NULL, status VARCHAR(32) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_resource_field (tenant_id, resource_id, field_code),
    UNIQUE KEY uk_resource_property_path (tenant_id, resource_id, property_path),
    CONSTRAINT ck_field_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_mask_strategy (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    strategy_code VARCHAR(64) NOT NULL, strategy_type VARCHAR(32) NOT NULL,
    config_json JSON NOT NULL, status VARCHAR(32) NOT NULL,
    delete_marker BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_mask_strategy_code (tenant_id, strategy_code, delete_marker),
    CONSTRAINT ck_mask_strategy_type CHECK (strategy_type IN ('PHONE','EMAIL','ID_CARD','BANK_CARD','NAME','ADDRESS','GENERIC_PARTIAL')),
    CONSTRAINT ck_mask_strategy_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_field_policy (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL, operation_id BIGINT UNSIGNED NOT NULL,
    field_id BIGINT UNSIGNED NOT NULL, readable TINYINT(1) NOT NULL,
    writable TINYINT(1) NOT NULL, hidden TINYINT(1) NOT NULL,
    mask_strategy_id BIGINT UNSIGNED NULL, priority INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), KEY idx_field_policy_lookup (tenant_id, resource_id, operation_id, field_id, status),
    CONSTRAINT ck_field_policy_status CHECK (status IN ('ACTIVE','DISABLED')),
    CONSTRAINT ck_field_policy_hidden CHECK (hidden = 0 OR (readable = 0 AND writable = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role_field_policy (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    role_permission_id BIGINT UNSIGNED NOT NULL, field_policy_id BIGINT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_role_field_policy (tenant_id, role_permission_id, field_policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_team_role_field_policy (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    team_role_permission_id BIGINT UNSIGNED NOT NULL, field_policy_id BIGINT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_team_role_field_policy (tenant_id, team_role_permission_id, field_policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
