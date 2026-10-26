package com.enterprise.iam.auth.application.port.out;

/** Revokes login sessions whose successful HTTP response was never confirmed. */
public interface OrphanLoginSessionReconciler {

    int reconcileExpired();
}
