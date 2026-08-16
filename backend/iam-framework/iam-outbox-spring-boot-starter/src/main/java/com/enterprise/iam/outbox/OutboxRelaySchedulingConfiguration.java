package com.enterprise.iam.outbox;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "iam.outbox.relay",
        name = "enabled",
        havingValue = "true")
class OutboxRelaySchedulingConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxHandlerRegistry outboxHandlerRegistry(List<OutboxEventHandler> handlers) {
        return new OutboxHandlerRegistry(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxRetryPolicy outboxRetryPolicy(OutboxRelayProperties properties) {
        properties.validateEnabled();
        return new OutboxRetryPolicy(
                properties.getInitialBackoff(), properties.getMaximumBackoff());
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxRelayObserver outboxRelayObserver(
            ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registries) {
        var registry = registries.getIfAvailable();
        return registry == null
                ? OutboxRelayObserver.noOp()
                : new MicrometerOutboxRelayObserver(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxRelay outboxRelay(
            OutboxRepository repository,
            OutboxHandlerRegistry handlers,
            OutboxRetryPolicy retryPolicy,
            OutboxRelayObserver observer,
            OutboxRelayProperties properties,
            @Qualifier("iamOutboxClock") Clock clock,
            Environment environment) {
        properties.validateEnabled();
        String applicationName = environment.getProperty("spring.application.name");
        return new OutboxRelay(
                repository,
                handlers,
                retryPolicy,
                observer,
                properties,
                clock,
                properties.resolveInstanceId(applicationName));
    }

    @Bean
    @ConditionalOnMissingBean
    ScheduledOutboxRelay scheduledOutboxRelay(OutboxRelay relay) {
        return new ScheduledOutboxRelay(relay);
    }
}
