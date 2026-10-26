package com.enterprise.iam.auth.application.service;

import com.enterprise.iam.auth.domain.exception.PasswordPolicyViolationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordChangePolicyTest {

    private final PasswordChangePolicy policy = new PasswordChangePolicy();

    @Test
    void normalizesToNfcBeforeCheckingUnicodeCodePointLength() {
        char[] proposed = ("a".repeat(14) + "e\u0301").toCharArray();

        char[] normalized = policy.normalizeNewPassword(proposed);

        try {
            assertThat(new String(normalized)).isEqualTo("a".repeat(14) + "é");
            assertThat(normalized).hasSize(15);
        } finally {
            Arrays.fill(normalized, '\0');
        }
    }

    @Test
    void countsSupplementaryUnicodeCharactersAsSingleCodePoints() {
        char[] proposed = "😀".repeat(15).toCharArray();

        char[] normalized = policy.normalizeNewPassword(proposed);

        try {
            assertThat(normalized).hasSize(30);
        } finally {
            Arrays.fill(normalized, '\0');
        }
    }

    @Test
    void rejectsBelowMinimumAndAboveMaximumWithoutTruncation() {
        assertThatThrownBy(() -> policy.normalizeNewPassword("a".repeat(14).toCharArray()))
                .isInstanceOf(PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> policy.normalizeNewPassword("a".repeat(129).toCharArray()))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }
}
