package com.enterprise.iam.audit.application.model;

public record RecordAuditLogResult(
        long auditLogId,
        long tenantId,
        long actorId,
        String action) {
}
