package com.enterprise.iam.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
                    .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                    .withBean(PlatformTransactionManager.class,
                            () -> mock(PlatformTransactionManager.class));

    @Test
    void exposesWriterAndRepositoryButKeepsRelayDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OutboxWriter.class);
            assertThat(context).hasSingleBean(OutboxRepository.class);
            assertThat(context).doesNotHaveBean(OutboxRelay.class);
            assertThat(context).doesNotHaveBean(ScheduledOutboxRelay.class);
        });
    }

    @Test
    void enabledRelayRequiresAtLeastOneExactHandler() {
        contextRunner
                .withPropertyValues("iam.outbox.relay.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining(
                                    "enabled outbox relay requires at least one handler");
                });
    }

    @Test
    void enabledRelayStartsWhenHandlerIsRegistered() {
        contextRunner
                .withPropertyValues(
                        "iam.outbox.relay.enabled=true",
                        "iam.outbox.relay.instance-id=test-instance")
                .withBean(OutboxEventHandler.class, OutboxAutoConfigurationTest::handler)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OutboxRelay.class);
                    assertThat(context).hasSingleBean(ScheduledOutboxRelay.class);
                });
    }

    private static OutboxEventHandler handler() {
        return new OutboxEventHandler() {
            @Override
            public String eventType() {
                return "event.test";
            }

            @Override
            public int schemaVersion() {
                return 1;
            }

            @Override
            public void handle(OutboxEvent event) {
            }
        };
    }
}
