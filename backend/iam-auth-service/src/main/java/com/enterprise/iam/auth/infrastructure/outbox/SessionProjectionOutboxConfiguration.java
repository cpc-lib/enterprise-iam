package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.application.port.out.SessionSecurityProjectionPublisher;
import com.enterprise.iam.outbox.OutboxEventHandler;
import com.enterprise.iam.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SessionProjectionOutboxConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SessionProjectionEventCodec sessionProjectionEventCodec(ObjectMapper objectMapper) {
        return new SessionProjectionEventCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(SessionProjectionOutboxAppender.class)
    SessionProjectionOutboxAppender sessionProjectionOutboxAppender(
            ObjectProvider<OutboxWriter> writer,
            SessionProjectionEventCodec codec) {
        return new JdbcSessionProjectionOutboxAppender(
                () -> writer.getIfAvailable(() -> {
                    throw new IllegalStateException(
                            "session projection append requires the JDBC outbox writer");
                }),
                codec);
    }

    @Bean
    @ConditionalOnBean(SessionSecurityProjectionPublisher.class)
    @ConditionalOnMissingBean(name = "sessionProjectionOutboxEventHandler")
    OutboxEventHandler sessionProjectionOutboxEventHandler(
            SessionProjectionEventCodec codec,
            SessionSecurityProjectionPublisher publisher) {
        return new SessionProjectionOutboxEventHandler(codec, publisher);
    }
}
