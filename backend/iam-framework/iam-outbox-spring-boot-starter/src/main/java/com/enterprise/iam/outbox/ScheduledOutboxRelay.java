package com.enterprise.iam.outbox;

import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

public final class ScheduledOutboxRelay {

    private final OutboxRelay relay;

    public ScheduledOutboxRelay(OutboxRelay relay) {
        this.relay = Objects.requireNonNull(relay, "relay must not be null");
    }

    @Scheduled(
            fixedDelayString = "${iam.outbox.relay.poll-delay:1s}",
            initialDelayString = "${iam.outbox.relay.poll-delay:1s}")
    public void poll() {
        relay.relayOnce();
    }
}
