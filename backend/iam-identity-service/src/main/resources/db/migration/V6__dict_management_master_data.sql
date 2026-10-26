-- Owner: iam-identity-service / iam_identity
-- Generic Data Dictionary (iam_dict_type and iam_dict_data) Master Data Tables

CREATE TABLE iam_dict_type (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    dict_type       VARCHAR(64) NOT NULL,
    dict_name       VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    description     VARCHAR(255) NULL,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type (tenant_id, dict_type, delete_marker),
    KEY idx_dict_type_tenant (tenant_id, status),
    CONSTRAINT ck_dict_type_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_dict_data (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    dict_type       VARCHAR(64) NOT NULL,
    dict_label      VARCHAR(128) NOT NULL,
    dict_value      VARCHAR(128) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    css_tag         VARCHAR(64) NULL,
    is_default      TINYINT UNSIGNED NOT NULL DEFAULT 0,
    description     VARCHAR(255) NULL,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_data_value (tenant_id, dict_type, dict_value, delete_marker),
    KEY idx_dict_data_type (tenant_id, dict_type, sort_order),
    CONSTRAINT ck_dict_data_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
