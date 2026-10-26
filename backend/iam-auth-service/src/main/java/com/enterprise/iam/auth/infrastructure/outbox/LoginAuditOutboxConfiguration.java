package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.auth.application.port.out.LoginAuditOutboxAppender;
import com.enterprise.iam.common.mq.DomainEventGateway;
import com.enterprise.iam.outbox.OutboxEventHandler;
import com.enterprise.iam.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LoginAuditOutboxConfiguration {

    @Bean
    @ConditionalOnMissingBean
    LoginAuditEventCodec loginAuditEventCodec(ObjectMapper objectMapper) {
        return new LoginAuditEventCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(LoginAuditOutboxAppender.class)
    LoginAuditOutboxAppender loginAuditOutboxAppender(
            ObjectProvider<OutboxWriter> writer,
            LoginAuditEventCodec codec) {
        return new JdbcLoginAuditOutboxAppender(
                () -> writer.getIfAvailable(() -> {
                    throw new IllegalStateException(
                            "login audit append requires the JDBC outbox writer");
                }),
                codec);
    }

    @Bean("loginSucceededAuditOutboxEventHandler")
    @ConditionalOnBean(DomainEventGateway.class)
    OutboxEventHandler loginSucceededAuditOutboxEventHandler(
            LoginAuditEventCodec codec,
            DomainEventGateway gateway,
            @Value("${iam.mq.exchange-name:iam.domain.events}") String exchange) {
        return new LoginAuditOutboxEventHandler(
                JdbcLoginAuditOutboxAppender.EVENT_TYPE_LOGIN_SUCCEEDED,
                codec,
                gateway,
                exchange);
    }

    @Bean("loginOrphanAuditOutboxEventHandler")
    @ConditionalOnBean(DomainEventGateway.class)
    OutboxEventHandler loginOrphanAuditOutboxEventHandler(
            LoginAuditEventCodec codec,
            DomainEventGateway gateway,
            @Value("${iam.mq.exchange-name:iam.domain.events}") String exchange) {
        return new LoginAuditOutboxEventHandler(
                JdbcLoginAuditOutboxAppender.EVENT_TYPE_ORPHAN_RECONCILED,
                codec,
                gateway,
                exchange);
    }

    @Bean("passwordChangedAuditOutboxEventHandler")
    @ConditionalOnBean(DomainEventGateway.class)
    OutboxEventHandler passwordChangedAuditOutboxEventHandler(
            LoginAuditEventCodec codec,
            DomainEventGateway gateway,
            @Value("${iam.mq.exchange-name:iam.domain.events}") String exchange) {
        return new LoginAuditOutboxEventHandler(
                JdbcLoginAuditOutboxAppender.EVENT_TYPE_PASSWORD_CHANGED,
                codec,
                gateway,
                exchange);
    }
}
