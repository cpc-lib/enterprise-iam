package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 41-bit millisecond time, 10-bit deployment node and 12-bit sequence. */
public final class TimeOrderedPositiveIdGenerator implements PositiveIdGenerator {

    static final long CUSTOM_EPOCH_MILLIS = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
    static final int MAX_NODE_ID = 1_023;
    static final int MAX_SEQUENCE = 4_095;

    private final Clock clock;
    private final int nodeId;
    private long lastTimestamp = -1;
    private int sequence;

    public TimeOrderedPositiveIdGenerator(Clock clock, int nodeId) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be 0..1023");
        }
        this.nodeId = nodeId;
    }

    @Override
    public synchronized long nextId() {
        long timestamp = clock.millis();
        if (timestamp < CUSTOM_EPOCH_MILLIS) {
            throw new IllegalStateException("clock precedes the ID epoch");
        }
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("clock moved backwards during ID generation");
        }
        if (timestamp == lastTimestamp) {
            if (sequence == MAX_SEQUENCE) {
                throw new IllegalStateException("ID sequence exhausted for the current millisecond");
            }
            sequence++;
        } else {
            lastTimestamp = timestamp;
            sequence = 0;
        }
        long elapsed = timestamp - CUSTOM_EPOCH_MILLIS;
        if (elapsed >= (1L << 41)) {
            throw new IllegalStateException("ID timestamp exceeds the supported range");
        }
        long id = (elapsed << 22) | ((long) nodeId << 12) | sequence;
        if (id <= 0) {
            throw new IllegalStateException("generated ID is not positive");
        }
        return id;
    }
}
