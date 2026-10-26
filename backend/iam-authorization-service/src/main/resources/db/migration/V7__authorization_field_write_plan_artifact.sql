-- Owner: iam-authorization-service / iam_authorization
-- Artifact table for typed field-write plan references (SPEC 38 §8, SPEC 47 D-11).

CREATE TABLE iam_field_write_plan_artifact (
    ref VARCHAR(256) NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    subject_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL,
    operation_id BIGINT UNSIGNED NOT NULL,
    permission_version BIGINT UNSIGNED NOT NULL,
    decision_id BIGINT UNSIGNED NOT NULL,
    payload_json JSON NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (ref),
    KEY idx_write_plan_lookup (tenant_id, subject_id, resource_id, operation_id),
    KEY idx_write_plan_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
