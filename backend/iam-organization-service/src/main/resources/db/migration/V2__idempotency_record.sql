-- Owner: iam-organization-service / iam_organization
-- JDBC-backed idempotency replay store (SPEC 18, SPEC 47 D-12).
-- Status values (IN_FLIGHT/COMMITTED) are constrained in application code;
-- no CHECK enum, so no G-1 REGISTRY entry is required.

CREATE TABLE iam_idempotency_record (
    tenant_id BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_status INT NULL,
    response_content_type VARCHAR(255) NULL,
    response_body LONGBLOB NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, idempotency_key),
    KEY idx_idempotency_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
