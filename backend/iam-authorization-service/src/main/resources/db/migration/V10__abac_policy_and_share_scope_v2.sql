-- Owner: iam-authorization-service / iam_authorization
-- Spec V2.0: ABAC policy table and share ACL projection scope columns (FOLDER_INHERITED, COLLECTION).

CREATE TABLE iam_abac_policy (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    policy_code     VARCHAR(64) NOT NULL,
    policy_name     VARCHAR(128) NOT NULL,
    resource_type   VARCHAR(64) NOT NULL,
    operation       VARCHAR(64) NOT NULL,
    effect          VARCHAR(16) NOT NULL,
    priority        INT NOT NULL DEFAULT 100,
    condition_expr  VARCHAR(2000) NOT NULL,
    description     VARCHAR(512) NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_iam_abac_effect CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT ck_iam_abac_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT uk_iam_abac_policy_code UNIQUE (tenant_id, policy_code)
);

CREATE INDEX idx_iam_abac_tenant_res_op
    ON iam_abac_policy (tenant_id, resource_type, operation, status, priority);

ALTER TABLE iam_share_acl_projection
    ADD COLUMN scope_type VARCHAR(32) NOT NULL DEFAULT 'INSTANCE';

ALTER TABLE iam_share_acl_projection
    ADD COLUMN folder_path VARCHAR(512) NULL;

ALTER TABLE iam_share_acl_projection
    ADD COLUMN collection_resource_ids_json VARCHAR(2000) NULL;

ALTER TABLE iam_share_acl_projection
    ADD CONSTRAINT ck_iam_share_proj_scope CHECK (scope_type IN ('INSTANCE', 'FOLDER_INHERITED', 'COLLECTION'));
