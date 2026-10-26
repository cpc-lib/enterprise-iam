package com.enterprise.iam.authorization.infrastructure.config;

import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.application.port.out.ShareAclProjectionRepository;
import com.enterprise.iam.authorization.infrastructure.mq.ShareAclProjectionApplier;
import com.enterprise.iam.authorization.infrastructure.mq.ShareAclProjectionListener;
import com.enterprise.iam.authorization.infrastructure.persistence.JdbcShareAclProjectionRepository;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.ShareAclProjectionMapper;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Spec 16 share ACL projection wiring. Default-off: activates only when
 * {@code iam.mq.enabled=true}, so unit contexts and non-MQ deployments never
 * instantiate the listener, queue bindings or projection applier.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.mq", name = "enabled", havingValue = "true")
public class ShareAclProjectionConfiguration {

    @Bean
    @ConditionalOnMissingBean(ShareAclProjectionRepository.class)
    public ShareAclProjectionRepository shareAclProjectionRepository(ShareAclProjectionMapper mapper) {
        return new JdbcShareAclProjectionRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionTemplate shareAclTransactionTemplate(
            PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public ShareAclProjectionApplier shareAclProjectionApplier(
            ShareAclProjectionMapper mapper,
            TransactionTemplate shareAclTransactionTemplate,
            PositiveIdGenerator positiveIdGenerator,
            Clock authorizationClock) {
        return new ShareAclProjectionApplier(
                mapper, shareAclTransactionTemplate, positiveIdGenerator, authorizationClock);
    }

    @Bean
    @ConditionalOnMissingBean
    public ShareAclProjectionListener shareAclProjectionListener(
            ShareAclProjectionApplier shareAclProjectionApplier,
            ObjectMapper iamMqObjectMapper) {
        return new ShareAclProjectionListener(shareAclProjectionApplier, iamMqObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "shareAclProjectionQueue")
    public Queue shareAclProjectionQueue(
            @Value("${iam.authorization.share-acl.queue:iam.authz.share-acl}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding shareAclGrantedBinding(
            Queue shareAclProjectionQueue,
            TopicExchange iamDomainEventExchange) {
        return BindingBuilder.bind(shareAclProjectionQueue)
                .to(iamDomainEventExchange)
                .with(ShareAclProjectionListener.ROUTING_KEY_GRANTED);
    }

    @Bean
    public Binding shareAclRevokedBinding(
            Queue shareAclProjectionQueue,
            TopicExchange iamDomainEventExchange) {
        return BindingBuilder.bind(shareAclProjectionQueue)
                .to(iamDomainEventExchange)
                .with(ShareAclProjectionListener.ROUTING_KEY_REVOKED);
    }
}
