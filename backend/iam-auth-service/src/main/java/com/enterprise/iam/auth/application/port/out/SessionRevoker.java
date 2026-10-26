package com.enterprise.iam.auth.application.port.out;

/**
 * Revokes a login session and all refresh tokens bound to it. Used by the
 * logout endpoint and force-logout flows.
 */
public interface SessionRevoker {

    void revokeSession(long tenantId, long sessionId);
}
