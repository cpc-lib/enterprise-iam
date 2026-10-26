package com.enterprise.iam.file.application.model;

/**
 * An active upload session whose expire_at has passed.
 */
public record ExpiredSession(
        long tenantId,
        long sessionId,
        long quotaReservationBytes,
        long version) {
}
