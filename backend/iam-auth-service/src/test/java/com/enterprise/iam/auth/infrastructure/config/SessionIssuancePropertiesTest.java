package com.enterprise.iam.auth.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionIssuancePropertiesTest {

    @Test
    void acceptsFrozenEnabledDefaultsWithExplicitNode() {
        SessionIssuanceProperties properties = new SessionIssuanceProperties();
        properties.setEnabled(true);
        properties.setNodeId(23);

        assertThatCode(properties::validateEnabled).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingNodeInvalidTtlOrderAndUnboundedSessionCount() {
        SessionIssuanceProperties missingNode = enabled();
        missingNode.setNodeId(null);
        assertThatThrownBy(missingNode::validateEnabled)
                .isInstanceOf(IllegalStateException.class);

        SessionIssuanceProperties invalidTtl = enabled();
        invalidTtl.setIdleTtl(Duration.ofDays(31));
        assertThatThrownBy(invalidTtl::validateEnabled)
                .isInstanceOf(IllegalStateException.class);

        SessionIssuanceProperties invalidCount = enabled();
        invalidCount.setMaximumConcurrentSessions(101);
        assertThatThrownBy(invalidCount::validateEnabled)
                .isInstanceOf(IllegalStateException.class);
    }

    private static SessionIssuanceProperties enabled() {
        SessionIssuanceProperties properties = new SessionIssuanceProperties();
        properties.setEnabled(true);
        properties.setNodeId(1);
        return properties;
    }
}
