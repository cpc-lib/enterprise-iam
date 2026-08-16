package com.enterprise.iam.outbox;

import java.time.Duration;

public interface OutboxRelayObserver {

    void claimed(int count);

    void completed(OutboxRelayOutcome outcome, Duration duration);

    static OutboxRelayObserver noOp() {
        return new OutboxRelayObserver() {
            @Override
            public void claimed(int count) {
            }

            @Override
            public void completed(OutboxRelayOutcome outcome, Duration duration) {
            }
        };
    }
}
