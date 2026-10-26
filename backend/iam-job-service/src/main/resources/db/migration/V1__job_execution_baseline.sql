CREATE TABLE IF NOT EXISTS iam_job_definition (
    id BIGINT NOT NULL PRIMARY KEY,
    job_code VARCHAR(64) NOT NULL,
    job_name VARCHAR(128) NOT NULL,
    description VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_iam_job_def_code UNIQUE (job_code),
    CONSTRAINT chk_iam_job_def_status CHECK (status IN ('ACTIVE', 'PAUSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS iam_job_execution_log (
    id BIGINT NOT NULL PRIMARY KEY,
    job_code VARCHAR(64) NOT NULL,
    execution_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) NULL,
    items_processed INT NOT NULL DEFAULT 0,
    items_converged INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_iam_job_exec_id UNIQUE (execution_id),
    CONSTRAINT chk_iam_job_exec_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    INDEX idx_iam_job_exec_code_created (job_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO iam_job_definition (id, job_code, job_name, description, cron_expression, status, created_at, updated_at)
VALUES
(1, 'SHARE_EXPIRATION_RECONCILE', '资源分享到期状态对齐任务', '周期性对齐已过期的资源分享记录与槽位状态收敛', '0 0/5 * * * ?', 'ACTIVE', NOW(3), NOW(3)),
(2, 'ORPHAN_SESSION_RECONCILE', '孤儿会话与登录交付回收任务', '回收超时未完成的登录交付凭据与孤儿会话记录', '0 0/10 * * * ?', 'ACTIVE', NOW(3), NOW(3)),
(3, 'STORAGE_QUOTA_AUDIT', '租户存储配额审计与重算任务', '审计各租户物理存储与逻辑配额一致性并执行自动校准', '0 0 2 * * ?', 'ACTIVE', NOW(3), NOW(3));
