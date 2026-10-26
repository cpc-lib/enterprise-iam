-- Owner: iam-identity-service / iam_identity
-- V2.0 Advanced Segregation of Duties (SoD) and Permission Approval Workflow

CREATE TABLE iam_sod_policy (
    id                        BIGINT PRIMARY KEY,
    tenant_id                 BIGINT NOT NULL,
    policy_code               VARCHAR(64) NOT NULL,
    policy_name               VARCHAR(128) NOT NULL,
    description               VARCHAR(512) NULL,
    conflicting_role_ids_json VARCHAR(2000) NOT NULL,
    max_allowed_roles         INT NOT NULL DEFAULT 1,
    enforcement_mode          VARCHAR(32) NOT NULL DEFAULT 'STATIC',
    severity                  VARCHAR(32) NOT NULL DEFAULT 'HIGH',
    status                    VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version                   BIGINT NOT NULL DEFAULT 1,
    created_at                TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_iam_sod_mode CHECK (enforcement_mode IN ('STATIC', 'DYNAMIC')),
    CONSTRAINT ck_iam_sod_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM')),
    CONSTRAINT ck_iam_sod_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT uk_iam_sod_policy_code UNIQUE (tenant_id, policy_code),
    KEY idx_iam_sod_tenant_status (tenant_id, status, id)
);

CREATE TABLE iam_permission_approval_request (
    id                BIGINT PRIMARY KEY,
    tenant_id         BIGINT NOT NULL,
    applicant_user_id BIGINT NOT NULL,
    request_type      VARCHAR(32) NOT NULL,
    target_id         BIGINT NOT NULL,
    target_name       VARCHAR(128) NOT NULL,
    justification     VARCHAR(1000) NOT NULL,
    duration_hours    INT NOT NULL DEFAULT 168,
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    approver_user_id  BIGINT NULL,
    decision_comment  VARCHAR(512) NULL,
    decided_at        TIMESTAMP(6) NULL,
    version           BIGINT NOT NULL DEFAULT 1,
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_iam_approval_type CHECK (request_type IN ('ROLE_ASSIGN', 'TEAM_ROLE_ASSIGN', 'RESOURCE_SHARE')),
    CONSTRAINT ck_iam_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    KEY idx_iam_approval_tenant_status (tenant_id, status, created_at)
);
