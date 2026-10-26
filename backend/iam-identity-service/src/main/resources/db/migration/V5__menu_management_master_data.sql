-- Owner: iam-identity-service / iam_identity
-- Menu Configuration and Dynamic Navigation Master Data Table

CREATE TABLE iam_menu (
    id              BIGINT UNSIGNED NOT NULL,
    tenant_id       BIGINT UNSIGNED NOT NULL,
    parent_id       BIGINT UNSIGNED NULL,
    menu_key        VARCHAR(64) NOT NULL,
    title           VARCHAR(128) NOT NULL,
    path            VARCHAR(256) NULL,
    icon            VARCHAR(64) NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    publish_status  VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    permission_code VARCHAR(64) NULL,
    component       VARCHAR(128) NULL,
    visible         TINYINT UNSIGNED NOT NULL DEFAULT 1,
    delete_marker   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_key (tenant_id, menu_key, delete_marker),
    KEY idx_menu_tenant_parent (tenant_id, parent_id, sort_order),
    CONSTRAINT ck_menu_status CHECK (status IN ('ACTIVE','DISABLED')),
    CONSTRAINT ck_menu_publish_status CHECK (publish_status IN ('DRAFT','PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
