package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.model.AuthenticationAttempt;
import com.enterprise.iam.auth.application.port.out.AuthenticationAttemptSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase-01 authentication attempt sink. Records structured security telemetry
 * to the application log. A future slice replaces this with an outbox-backed
 * audit-event publisher.
 */
public final class Slf4jAuthenticationAttemptSink implements AuthenticationAttemptSink {

    private static final Logger log = LoggerFactory.getLogger(Slf4jAuthenticationAttemptSink.class);

    @Override
    public void record(AuthenticationAttempt attempt) {
        log.info(
                "authentication attempt successful={} tenantId={} userId={} failureReason={} requestId={}",
                attempt.successful(),
                attempt.tenantId(),
                attempt.userId(),
                attempt.failureReason(),
                attempt.requestId());
    }
}
