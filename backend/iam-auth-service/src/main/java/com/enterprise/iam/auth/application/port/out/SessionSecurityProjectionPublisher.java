package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.common.security.session.SessionSecurityProjection;

@FunctionalInterface
public interface SessionSecurityProjectionPublisher {

    ProjectionWriteResult publish(SessionSecurityProjection projection);
}
