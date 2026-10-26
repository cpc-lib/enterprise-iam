package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.auth.domain.model.ActiveSession;

import java.util.List;

public interface SessionQueryRepository {

    List<ActiveSession> findSessions(long tenantId, Long userId, String status, int offset, int limit);

    long countSessions(long tenantId, Long userId, String status);

    boolean revokeSession(long tenantId, long sessionId, String reason);
}
