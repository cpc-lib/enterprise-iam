package com.enterprise.iam.audit.infrastructure.config;

import com.enterprise.iam.audit.infrastructure.mq.AuthLoginAuditEventListener;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

class AuthLoginAuditMqConfigurationTest {

    @Test
    void declaresDurableQueueAndAllBindings() {
        AuthLoginAuditMqConfiguration configuration = new AuthLoginAuditMqConfiguration();
        Queue queue = configuration.authLoginAuditQueue("iam.audit.auth-login");
        TopicExchange exchange = new TopicExchange("iam.domain.events", true, false);

        Binding succeeded = configuration.authLoginSucceededAuditBinding(queue, exchange);
        Binding orphan = configuration.authLoginOrphanReconciledAuditBinding(queue, exchange);
        Binding passwordChanged = configuration.authPasswordChangedAuditBinding(queue, exchange);

        assertThat(queue.isDurable()).isTrue();
        assertThat(succeeded.getRoutingKey())
                .isEqualTo(AuthLoginAuditEventListener.EVENT_LOGIN_SUCCEEDED);
        assertThat(orphan.getRoutingKey())
                .isEqualTo(AuthLoginAuditEventListener.EVENT_ORPHAN_RECONCILED);
        assertThat(passwordChanged.getRoutingKey())
                .isEqualTo(AuthLoginAuditEventListener.EVENT_PASSWORD_CHANGED);
    }
}
