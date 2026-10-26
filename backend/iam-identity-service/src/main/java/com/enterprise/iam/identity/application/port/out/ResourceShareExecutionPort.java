package com.enterprise.iam.identity.application.port.out;

public interface ResourceShareExecutionPort {

    void grantResourceShare(long tenantId, long userId, long resourceId, int durationHours);
}
