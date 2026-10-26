-- Idempotency fence for audit events consumed from iam-auth-service.
ALTER TABLE iam_audit_log
    ADD COLUMN source_service VARCHAR(64) NULL AFTER created_at,
    ADD COLUMN source_event_id BIGINT UNSIGNED NULL AFTER source_service,
    ADD UNIQUE KEY uk_audit_source_event (source_service, source_event_id);
