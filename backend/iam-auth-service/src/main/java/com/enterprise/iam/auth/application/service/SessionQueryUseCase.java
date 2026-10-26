package com.enterprise.iam.auth.application.service;

import com.enterprise.iam.auth.application.port.out.SessionQueryRepository;
import com.enterprise.iam.auth.domain.model.ActiveSession;

import java.util.List;
import java.util.Objects;

public class SessionQueryUseCase {

    private final SessionQueryRepository sessionQueryRepository;

    public SessionQueryUseCase(SessionQueryRepository sessionQueryRepository) {
        this.sessionQueryRepository = Objects.requireNonNull(sessionQueryRepository);
    }

    public SessionPageResult listSessions(long tenantId, Long userId, String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safePageSize;

        String safeStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        List<ActiveSession> items = sessionQueryRepository.findSessions(tenantId, userId, safeStatus, offset, safePageSize);
        long total = sessionQueryRepository.countSessions(tenantId, userId, safeStatus);

        return new SessionPageResult(items, total, safePage, safePageSize);
    }

    public boolean revokeSession(long tenantId, long sessionId, String reason) {
        String safeReason = (reason != null && !reason.isBlank()) ? reason.trim() : "ADMIN_MANUAL_REVOKE";
        return sessionQueryRepository.revokeSession(tenantId, sessionId, safeReason);
    }

    public record SessionPageResult(
            List<ActiveSession> items,
            long total,
            int page,
            int pageSize
    ) {}
}
