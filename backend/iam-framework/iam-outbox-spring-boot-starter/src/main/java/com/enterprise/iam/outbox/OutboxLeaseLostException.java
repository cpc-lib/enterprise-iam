package com.enterprise.iam.outbox;

/** The row is no longer owned by this relay instance; no blind update is made. */
public final class OutboxLeaseLostException extends RuntimeException {

    public OutboxLeaseLostException(long eventRowId) {
        super("outbox claim ownership was lost for row " + eventRowId);
    }
}
