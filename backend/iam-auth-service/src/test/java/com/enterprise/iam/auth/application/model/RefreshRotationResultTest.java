package com.enterprise.iam.auth.application.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshRotationResultTest {

    private static IssuedLoginSession session() {
        return new IssuedLoginSession(
                "header.payload.signature",
                300,
                new SensitiveRefreshToken("rt1.test-key.token-value-0123456789abcdef".toCharArray()),
                1_209_600,
                101,
                20,
                10);
    }

    @Test
    void rotatedCarriesFrozenSuccessCodeAndSession() {
        IssuedLoginSession issued = session();
        RefreshRotationResult result = RefreshRotationResult.rotated(issued);

        assertThat(result.rotated()).isTrue();
        assertThat(result.publicCode()).isEqualTo("OK");
        assertThat(result.issuedSession()).contains(issued);
    }

    @Test
    void rejectedCarriesFrozenFailureCodeWithoutSession() {
        RefreshRotationResult result = RefreshRotationResult.rejected();

        assertThat(result.rotated()).isFalse();
        assertThat(result.publicCode()).isEqualTo("IAM_AUTHENTICATION_FAILED");
        assertThat(result.issuedSession()).isEmpty();
    }

    @Test
    void inconsistentStateIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new RefreshRotationResult(true, "OK", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshRotationResult(false, "IAM_AUTHENTICATION_FAILED", session()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshRotationResult(true, "WEIRD", session()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frozen public outcome");
    }

    @Test
    void toStringNeverRendersTheRefreshToken() {
        IssuedLoginSession issued = session();
        char[] raw = issued.refreshToken().copyValue();

        try {
            String rendered = RefreshRotationResult.rotated(issued).toString();
            assertThat(rendered).contains("REDACTED");
            assertThat(rendered).doesNotContain(new String(raw));
        } finally {
            Arrays.fill(raw, '\0');
            issued.refreshToken().destroy();
        }
    }
}
