-- Owner: iam-file-service / iam_file
-- V2.0 Dynamic Watermark, Content DLP / Archive-Bomb Inspection, Storage Tiering & S204 DR Drill Log

ALTER TABLE iam_file ADD COLUMN storage_tier VARCHAR(32) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE iam_file ADD COLUMN dlp_status VARCHAR(32) NOT NULL DEFAULT 'PASS';
ALTER TABLE iam_file ADD COLUMN watermark_policy VARCHAR(32) NOT NULL DEFAULT 'DYNAMIC_TEXT';
ALTER TABLE iam_file ADD CONSTRAINT ck_iam_file_storage_tier CHECK (storage_tier IN ('STANDARD', 'INFREQUENT_ACCESS', 'GLACIER_ARCHIVE'));
ALTER TABLE iam_file ADD CONSTRAINT ck_iam_file_dlp_status CHECK (dlp_status IN ('PASS', 'MASKED', 'BLOCKED'));

CREATE TABLE iam_file_dr_drill_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    scenario_code VARCHAR(32) NOT NULL,
    target_rpo_seconds INT NOT NULL,
    actual_rpo_seconds INT NOT NULL,
    target_rto_seconds INT NOT NULL,
    actual_rto_seconds INT NOT NULL,
    verified_objects INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    executed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_iam_file_dr_scenario CHECK (scenario_code IN ('SCENARIO_A_OBJECT_CORRUPTION', 'SCENARIO_B_BUCKET_LOSS', 'SCENARIO_C_DB_PITR', 'SCENARIO_D_FULL_REGION_FAILOVER')),
    CONSTRAINT ck_iam_file_dr_status CHECK (status IN ('PASSED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
