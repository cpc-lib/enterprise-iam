-- Owner: iam-file-service / iam_file

CREATE TABLE iam_file_object (
    id BIGINT UNSIGNED NOT NULL, sha256 BINARY(32) NOT NULL,
    file_size BIGINT UNSIGNED NOT NULL, bucket_name VARCHAR(128) NOT NULL,
    object_key VARCHAR(512) NOT NULL, storage_version VARCHAR(128) NULL,
    storage_tier VARCHAR(16) NOT NULL DEFAULT 'STANDARD', status VARCHAR(32) NOT NULL,
    reference_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_file_object_hash (sha256, file_size),
    UNIQUE KEY uk_file_object_key (bucket_name, object_key),
    CONSTRAINT ck_file_object_status CHECK (status IN ('ACTIVE','QUARANTINED','MISSING','PURGING','PURGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_file (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    object_id BIGINT UNSIGNED NOT NULL, file_name VARCHAR(255) NOT NULL,
    extension VARCHAR(32) NULL, declared_content_type VARCHAR(255) NOT NULL,
    detected_content_type VARCHAR(255) NULL, file_size BIGINT UNSIGNED NOT NULL,
    sha256 BINARY(32) NOT NULL, owner_user_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL, scan_status VARCHAR(32) NOT NULL,
    retention_until DATETIME(3) NULL, legal_hold TINYINT(1) NOT NULL DEFAULT 0,
    delete_marker BIGINT UNSIGNED NOT NULL DEFAULT 0, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), KEY idx_file_tenant_status (tenant_id, status, created_at, id),
    KEY idx_file_hash (tenant_id, sha256, file_size),
    CONSTRAINT ck_file_status CHECK (status IN ('VERIFYING','SCANNING','AVAILABLE','QUARANTINED','INFECTED','DELETED','ARCHIVED')),
    CONSTRAINT ck_file_scan_status CHECK (scan_status IN ('PENDING','SCANNING','CLEAN','INFECTED','FAILED','NOT_REQUIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_file_reference (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    file_id BIGINT UNSIGNED NOT NULL, resource_id BIGINT UNSIGNED NOT NULL,
    resource_instance_key VARCHAR(128) NOT NULL, reference_type VARCHAR(32) NOT NULL,
    field_id BIGINT UNSIGNED NOT NULL DEFAULT 0, sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL, active_slot TINYINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_reference_active (tenant_id, file_id, resource_id, resource_instance_key, field_id, active_slot),
    KEY idx_file_reference_resource (tenant_id, resource_id, resource_instance_key, field_id, status),
    CONSTRAINT ck_file_reference_status CHECK (status IN ('ACTIVE','DELETED')),
    CONSTRAINT ck_file_reference_slot CHECK (active_slot IS NULL OR active_slot = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_file_upload_session (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL, file_name VARCHAR(255) NOT NULL,
    file_size BIGINT UNSIGNED NOT NULL, whole_sha256 BINARY(32) NOT NULL,
    declared_content_type VARCHAR(255) NOT NULL, storage_upload_id VARCHAR(255) NULL,
    bucket_name VARCHAR(128) NOT NULL, object_key VARCHAR(512) NOT NULL,
    part_size BIGINT UNSIGNED NOT NULL, part_count INT UNSIGNED NOT NULL,
    uploaded_part_count INT UNSIGNED NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL,
    expire_at DATETIME(3) NOT NULL, completed_file_id BIGINT UNSIGNED NULL,
    failure_code VARCHAR(128) NULL, quota_reservation_bytes BIGINT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_upload_object_key (bucket_name, object_key),
    KEY idx_upload_resume (tenant_id, user_id, status, updated_at, id),
    KEY idx_upload_expiry (status, expire_at, id),
    CONSTRAINT ck_upload_status CHECK (status IN ('INIT','UPLOADING','VERIFYING','MERGING','COMPLETED','FAILED','ABORTED','EXPIRED')),
    CONSTRAINT ck_upload_parts CHECK (part_count BETWEEN 1 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_file_upload_part (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    upload_session_id BIGINT UNSIGNED NOT NULL, part_number INT UNSIGNED NOT NULL,
    part_size BIGINT UNSIGNED NOT NULL, part_sha256 BINARY(32) NULL,
    storage_etag VARCHAR(255) NULL, status VARCHAR(32) NOT NULL,
    uploaded_at DATETIME(3) NULL, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_upload_part (tenant_id, upload_session_id, part_number),
    CONSTRAINT ck_upload_part_number CHECK (part_number BETWEEN 1 AND 10000),
    CONSTRAINT ck_upload_part_status CHECK (status IN ('PENDING','UPLOADING','UPLOADED','VERIFIED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_file_scan_record (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    file_id BIGINT UNSIGNED NOT NULL, scan_attempt INT UNSIGNED NOT NULL,
    scanner VARCHAR(64) NOT NULL, signature_version VARCHAR(64) NULL,
    result VARCHAR(32) NOT NULL, threat_code VARCHAR(128) NULL,
    error_code VARCHAR(128) NULL, started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_file_scan_attempt (tenant_id, file_id, scan_attempt),
    KEY idx_file_scan_result (tenant_id, result, created_at),
    CONSTRAINT ck_file_scan_result CHECK (result IN ('SCANNING','CLEAN','INFECTED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_file_tenant_quota (
    id BIGINT UNSIGNED NOT NULL, tenant_id BIGINT UNSIGNED NOT NULL,
    quota_bytes BIGINT UNSIGNED NOT NULL, used_logical_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reserved_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0, max_file_bytes BIGINT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_file_tenant_quota (tenant_id),
    CONSTRAINT ck_file_quota_nonnegative CHECK (used_logical_bytes + reserved_bytes <= quota_bytes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
