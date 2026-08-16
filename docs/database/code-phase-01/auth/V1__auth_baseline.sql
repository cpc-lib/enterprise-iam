-- Owner: iam-auth-service / iam_auth
-- Secrets are injected. Plain passwords and refresh tokens MUST NOT be stored.

CREATE TABLE iam_password_credential (
    id                  BIGINT UNSIGNED NOT NULL,
    tenant_id           BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    password_phc        VARCHAR(255) NOT NULL,
    password_version    BIGINT UNSIGNED NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL,
    changed_at          DATETIME(3) NOT NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_user (tenant_id, user_id),
    CONSTRAINT ck_password_status CHECK (status IN ('ACTIVE','RESET_REQUIRED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_login_session (
    id                  BIGINT UNSIGNED NOT NULL,
    tenant_id           BIGINT UNSIGNED NOT NULL,
    session_id          BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    status              VARCHAR(32) NOT NULL,
    device_id_hash      BINARY(32) NULL,
    device_type         VARCHAR(32) NULL,
    user_agent_hash     BINARY(32) NULL,
    login_ip_prefix     VARBINARY(16) NULL,
    last_access_at      DATETIME(3) NOT NULL,
    idle_expire_at      DATETIME(3) NOT NULL,
    absolute_expire_at  DATETIME(3) NOT NULL,
    last_strong_auth_at DATETIME(3) NOT NULL,
    revoked_at          DATETIME(3) NULL,
    revoke_reason       VARCHAR(128) NULL,
    session_version     BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_login_session_id (session_id),
    KEY idx_session_user (tenant_id, user_id, status, absolute_expire_at),
    KEY idx_session_cleanup (status, idle_expire_at, id),
    CONSTRAINT ck_session_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_session_expiry CHECK (absolute_expire_at > created_at AND idle_expire_at <= absolute_expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_refresh_token (
    id                      BIGINT UNSIGNED NOT NULL,
    tenant_id               BIGINT UNSIGNED NOT NULL,
    user_id                 BIGINT UNSIGNED NOT NULL,
    session_id              BIGINT UNSIGNED NOT NULL,
    token_family_id         BIGINT UNSIGNED NOT NULL,
    token_hash              BINARY(32) NOT NULL COMMENT 'HMAC-SHA-256; raw token is never stored',
    parent_token_id         BIGINT UNSIGNED NULL,
    replaced_by_token_id    BIGINT UNSIGNED NULL,
    status                  VARCHAR(32) NOT NULL,
    issued_at               DATETIME(3) NOT NULL,
    expire_at               DATETIME(3) NOT NULL,
    rotated_at              DATETIME(3) NULL,
    revoked_at              DATETIME(3) NULL,
    revoke_reason           VARCHAR(128) NULL,
    version                 BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_hash (token_hash),
    KEY idx_refresh_session (tenant_id, session_id, status, expire_at),
    KEY idx_refresh_family (tenant_id, token_family_id, status),
    KEY idx_refresh_cleanup (status, expire_at, id),
    CONSTRAINT ck_refresh_status CHECK (status IN ('ACTIVE','ROTATED','REVOKED','REUSED','EXPIRED')),
    CONSTRAINT ck_refresh_expiry CHECK (expire_at > issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_security_state (
    id                      BIGINT UNSIGNED NOT NULL,
    tenant_id               BIGINT UNSIGNED NOT NULL,
    user_id                 BIGINT UNSIGNED NOT NULL,
    token_version           BIGINT UNSIGNED NOT NULL DEFAULT 1,
    password_version        BIGINT UNSIGNED NOT NULL DEFAULT 1,
    login_failure_count     INT UNSIGNED NOT NULL DEFAULT 0,
    failure_window_start_at DATETIME(3) NULL,
    lock_until              DATETIME(3) NULL,
    last_password_change_at DATETIME(3) NULL,
    version                 BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_security_user (tenant_id, user_id),
    KEY idx_security_lock (lock_until, tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
