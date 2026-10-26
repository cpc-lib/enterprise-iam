-- Owner: iam-organization-service / iam_organization
-- Position / Job Title master data table

CREATE TABLE iam_position (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    position_code   VARCHAR(64) NOT NULL,
    position_name   VARCHAR(128) NOT NULL,
    organization_id BIGINT UNSIGNED NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    description     VARCHAR(500) NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_position_code (tenant_id, position_code, delete_marker),
    KEY idx_position_tenant_status (tenant_id, status, sort_order),
    KEY idx_position_org (tenant_id, organization_id, status),
    CONSTRAINT ck_position_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
