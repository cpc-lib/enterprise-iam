package com.enterprise.iam.identity.application.port.out;

public interface UserSessionRevocationPort {

    void kickUserSessions(long tenantId, long userId);
}
