package com.enterprise.iam.auth.application.service;

import com.enterprise.iam.auth.domain.exception.PasswordPolicyViolationException;

import java.nio.CharBuffer;
import java.text.Normalizer;
import java.util.Objects;

/** Frozen Unicode password policy from SECURITY-PARAMETERS.yaml. */
public final class PasswordChangePolicy {

    public static final int MINIMUM_CODE_POINTS = 8;
    public static final int MAXIMUM_CODE_POINTS = 128;

    public char[] normalizeNewPassword(char[] proposedPassword) {
        Objects.requireNonNull(proposedPassword, "proposedPassword must not be null");
        String normalized = Normalizer.normalize(
                CharBuffer.wrap(proposedPassword), Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MINIMUM_CODE_POINTS || length > MAXIMUM_CODE_POINTS) {
            throw new PasswordPolicyViolationException();
        }
        return normalized.toCharArray();
    }
}
