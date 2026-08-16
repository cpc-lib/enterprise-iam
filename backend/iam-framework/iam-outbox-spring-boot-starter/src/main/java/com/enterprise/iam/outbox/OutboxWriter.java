package com.enterprise.iam.outbox;

@FunctionalInterface
public interface OutboxWriter {

    /** Must join an already-active write transaction; it never starts one. */
    void append(OutboxEventToAppend event);
}
