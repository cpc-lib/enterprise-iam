-- Owner: iam-identity-service / iam_identity
-- MySQL 8.4 LTS; connection timezone MUST be UTC.

CREATE TABLE iam_tenant (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_code     VARCHAR(64) NOT NULL,
    tenant_name     VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code, delete_marker),
    KEY idx_tenant_status (status, id),
    CONSTRAINT ck_tenant_status CHECK (status IN ('INITIALIZING','ACTIVE','SUSPENDED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    username        VARCHAR(128) NULL,
    display_name    VARCHAR(128) NOT NULL,
    email           VARCHAR(320) NULL,
    phone           VARCHAR(32) NULL,
    status          VARCHAR(32) NOT NULL,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (tenant_id, username, delete_marker),
    KEY idx_user_tenant_status (tenant_id, status, id),
    CONSTRAINT ck_user_status CHECK (status IN ('INVITED','ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_identity (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    identity_type   VARCHAR(32) NOT NULL,
    identity_key    VARBINARY(64) NOT NULL COMMENT 'HMAC of canonical identity; key is not stored in DB',
    credential_ref  BIGINT UNSIGNED NULL,
    status          VARCHAR(32) NOT NULL,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_identity (tenant_id, identity_type, identity_key, delete_marker),
    KEY idx_identity_user (tenant_id, user_id, status),
    CONSTRAINT ck_identity_type CHECK (identity_type IN ('USERNAME','EMAIL','PHONE')),
    CONSTRAINT ck_identity_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    role_code       VARCHAR(64) NOT NULL,
    role_name       VARCHAR(128) NOT NULL,
    description     VARCHAR(500) NULL,
    priority        INT NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (tenant_id, role_code, delete_marker),
    KEY idx_role_tenant_status (tenant_id, status, id),
    CONSTRAINT ck_role_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_role (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    role_id         BIGINT UNSIGNED NOT NULL,
    status          VARCHAR(32) NOT NULL,
    active_slot     TINYINT UNSIGNED NULL COMMENT '1 for active; NULL after revoke',
    start_time      DATETIME(3) NOT NULL,
    expire_time     DATETIME(3) NULL,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role_active (tenant_id, user_id, role_id, active_slot),
    KEY idx_user_role_subject (tenant_id, user_id, status, expire_time),
    KEY idx_user_role_role (tenant_id, role_id, status),
    CONSTRAINT ck_user_role_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_user_role_slot CHECK (active_slot IS NULL OR active_slot = 1),
    CONSTRAINT ck_user_role_time CHECK (expire_time IS NULL OR expire_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
