package com.enterprise.iam.audit.infrastructure.config;

import com.enterprise.iam.audit.application.service.RecordSourcedAuditLogUseCase;
import com.enterprise.iam.audit.infrastructure.mq.AuthLoginAuditEventListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.mq", name = "enabled", havingValue = "true")
public class AuthLoginAuditMqConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthLoginAuditEventListener authLoginAuditEventListener(
            RecordSourcedAuditLogUseCase recordSourcedAuditLogUseCase,
            ObjectMapper objectMapper) {
        return new AuthLoginAuditEventListener(recordSourcedAuditLogUseCase, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "authLoginAuditQueue")
    public Queue authLoginAuditQueue(
            @Value("${iam.audit.auth-login.queue:iam.audit.auth-login}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding authLoginSucceededAuditBinding(
            Queue authLoginAuditQueue,
            TopicExchange iamDomainEventExchange) {
        return BindingBuilder.bind(authLoginAuditQueue)
                .to(iamDomainEventExchange)
                .with(AuthLoginAuditEventListener.EVENT_LOGIN_SUCCEEDED);
    }

    @Bean
    public Binding authLoginOrphanReconciledAuditBinding(
            Queue authLoginAuditQueue,
            TopicExchange iamDomainEventExchange) {
        return BindingBuilder.bind(authLoginAuditQueue)
                .to(iamDomainEventExchange)
                .with(AuthLoginAuditEventListener.EVENT_ORPHAN_RECONCILED);
    }

    @Bean
    public Binding authPasswordChangedAuditBinding(
            Queue authLoginAuditQueue,
            TopicExchange iamDomainEventExchange) {
        return BindingBuilder.bind(authLoginAuditQueue)
                .to(iamDomainEventExchange)
                .with(AuthLoginAuditEventListener.EVENT_PASSWORD_CHANGED);
    }
}
