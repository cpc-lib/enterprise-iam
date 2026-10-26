-- Owner: iam-organization-service / iam_organization

CREATE TABLE iam_organization (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    org_code VARCHAR(64) NOT NULL, org_name VARCHAR(128) NOT NULL,
    parent_id BIGINT UNSIGNED NULL, materialized_path VARCHAR(2048) NOT NULL,
    depth SMALLINT UNSIGNED NOT NULL, status VARCHAR(32) NOT NULL,
    delete_marker BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_organization_code (tenant_id, org_code, delete_marker),
    KEY idx_organization_parent (tenant_id, parent_id, status),
    KEY idx_organization_path (tenant_id, materialized_path(512)),
    CONSTRAINT ck_organization_depth CHECK (depth <= 32),
    CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_team (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    organization_id BIGINT UNSIGNED NOT NULL, parent_team_id BIGINT UNSIGNED NULL,
    team_code VARCHAR(64) NOT NULL, team_name VARCHAR(128) NOT NULL,
    materialized_path VARCHAR(2048) NOT NULL, depth SMALLINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL, delete_marker BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_code (tenant_id, team_code, delete_marker),
    KEY idx_team_parent (tenant_id, parent_team_id, status),
    KEY idx_team_organization (tenant_id, organization_id, status),
    KEY idx_team_path (tenant_id, materialized_path(512)),
    CONSTRAINT ck_team_depth CHECK (depth <= 32),
    CONSTRAINT ck_team_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_team_member (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    team_id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL,
    membership_status VARCHAR(32) NOT NULL, active_slot TINYINT UNSIGNED NULL,
    joined_at DATETIME(3) NOT NULL, left_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_member_active (tenant_id, team_id, user_id, active_slot),
    KEY idx_team_member_user (tenant_id, user_id, membership_status, team_id),
    CONSTRAINT ck_team_member_status CHECK (membership_status IN ('ACTIVE','LEFT')),
    CONSTRAINT ck_team_member_slot CHECK (active_slot IS NULL OR active_slot = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_team_role (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    team_id BIGINT UNSIGNED NOT NULL, role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL, description VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL, delete_marker BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_role_code (tenant_id, team_id, role_code, delete_marker),
    KEY idx_team_role_team (tenant_id, team_id, status),
    CONSTRAINT ck_team_role_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_team_member_role (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    team_id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL,
    team_role_id BIGINT UNSIGNED NOT NULL, status VARCHAR(32) NOT NULL,
    active_slot TINYINT UNSIGNED NULL, start_time DATETIME(3) NOT NULL,
    expire_time DATETIME(3) NULL, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_member_role_active (tenant_id, team_id, user_id, team_role_id, active_slot),
    KEY idx_team_member_role_user (tenant_id, user_id, status, expire_time),
    CONSTRAINT ck_team_member_role_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_team_member_role_slot CHECK (active_slot IS NULL OR active_slot = 1),
    CONSTRAINT ck_team_member_role_time CHECK (expire_time IS NULL OR expire_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
