package com.enterprise.iam.sharing.infrastructure.outbox;

import com.enterprise.iam.common.mq.DomainEventGateway;
import com.enterprise.iam.outbox.OutboxEventHandler;
import com.enterprise.iam.outbox.OutboxWriter;
import com.enterprise.iam.sharing.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.sharing.application.port.out.ShareAclEventAppender;
import com.enterprise.iam.sharing.application.port.out.ShareProjectionEpochStore;
import com.enterprise.iam.sharing.infrastructure.persistence.JdbcShareProjectionEpochStore;
import com.enterprise.iam.sharing.infrastructure.persistence.mapper.SharingPersistenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the share ACL outbox appender and, only when an MQ gateway exists
 * ({@code iam.mq.enabled=true}), the two relay handlers. When MQ is disabled
 * the appender still commits events inside the business transaction while
 * the relay keeps them unrelayed, mirroring the auth-service gating.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iam.sharing", name = "enabled", havingValue = "true")
public class ShareAclOutboxConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ShareAclEventCodec shareAclEventCodec(ObjectMapper objectMapper) {
        return new ShareAclEventCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ShareProjectionEpochStore.class)
    ShareProjectionEpochStore shareProjectionEpochStore(
            SharingPersistenceMapper mapper,
            PositiveIdGenerator idGenerator) {
        return new JdbcShareProjectionEpochStore(mapper, idGenerator);
    }

    @Bean
    @ConditionalOnMissingBean(ShareAclEventAppender.class)
    ShareAclEventAppender shareAclEventAppender(
            ObjectProvider<OutboxWriter> writer,
            ShareAclEventCodec codec,
            PositiveIdGenerator idGenerator) {
        return new JdbcShareAclEventAppender(
                () -> writer.getIfAvailable(() -> {
                    throw new IllegalStateException(
                            "share ACL event append requires the JDBC outbox writer");
                }),
                codec,
                idGenerator);
    }

    @Bean
    @ConditionalOnBean(DomainEventGateway.class)
    @ConditionalOnMissingBean(name = "shareAclGrantedOutboxEventHandler")
    OutboxEventHandler shareAclGrantedOutboxEventHandler(
            ShareAclEventCodec codec,
            DomainEventGateway gateway,
            @Value("${iam.mq.exchange-name:iam.domain.events}") String exchange) {
        return new ShareAclGrantedOutboxEventHandler(codec, gateway, exchange);
    }

    @Bean
    @ConditionalOnBean(DomainEventGateway.class)
    @ConditionalOnMissingBean(name = "shareAclRevokedOutboxEventHandler")
    OutboxEventHandler shareAclRevokedOutboxEventHandler(
            ShareAclEventCodec codec,
            DomainEventGateway gateway,
            @Value("${iam.mq.exchange-name:iam.domain.events}") String exchange) {
        return new ShareAclRevokedOutboxEventHandler(codec, gateway, exchange);
    }
}
