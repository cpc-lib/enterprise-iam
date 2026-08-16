package com.enterprise.iam.auth.application.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveTokenRedactionTest {

    @Test
    void refreshBufferIsCopyingDestroyableAndNeverRendered() {
        char[] source = "rt1.key.0123456789012345678901234567890123456789012".toCharArray();
        SensitiveRefreshToken token = new SensitiveRefreshToken(source);
        source[8] = 'X';

        char[] copy = token.copyValue();
        assertThat(copy[8]).isNotEqualTo('X');
        assertThat(token.toString()).doesNotContain(new String(copy)).contains("REDACTED");

        token.destroy();
        assertThat(token.isDestroyed()).isTrue();
        assertThatThrownBy(token::copyValue).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loginModelsRedactBothCredentialValues() {
        SensitiveRefreshToken refresh = new SensitiveRefreshToken(
                "rt1.key.0123456789012345678901234567890123456789012".toCharArray());
        IssuedLoginSession session = new IssuedLoginSession(
                "header.payload.signature", 300, refresh, 1_209_600, 30, 20, 10);
        LoginResult result = LoginResult.authenticated(session);

        assertThat(session.toString())
                .doesNotContain("header.payload.signature", "0123456789")
                .contains("REDACTED");
        assertThat(result.toString())
                .doesNotContain("header.payload.signature", "0123456789")
                .contains("REDACTED");
    }
}
