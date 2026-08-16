package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.auth.application.command.RefreshRotationCommand;
import com.enterprise.iam.auth.application.model.RefreshRotationResult;

/**
 * Rotates a presented opaque refresh token per RFC 9700: one successor token,
 * at-most-once semantics via CAS, and whole-family revocation on replay.
 * The command is one-shot; implementations destroy the presented buffer.
 */
public interface RefreshTokenRotator {

    RefreshRotationResult rotate(RefreshRotationCommand command);
}
