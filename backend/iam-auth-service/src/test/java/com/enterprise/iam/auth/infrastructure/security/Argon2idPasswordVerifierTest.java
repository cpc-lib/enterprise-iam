package com.enterprise.iam.auth.infrastructure.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Argon2idPasswordVerifierTest {

    private Argon2idPasswordVerifier verifier;

    @BeforeAll
    void setUp() {
        verifier = new Argon2idPasswordVerifier();
    }

    @Test
    void emitsFrozenArgon2idPhcAndVerifiesNfcEquivalentPasswords() {
        char[] composed = "pássword-value".toCharArray();
        char[] decomposed = "pa\u0301ssword-value".toCharArray();

        String phc = verifier.encode(composed);

        assertThat(phc).startsWith("$argon2id$");
        assertThat(phc).contains("m=19456,t=2,p=1");
        assertThat(verifier.verify(decomposed, phc)).isTrue();
        assertThat(verifier.verify("wrong-password".toCharArray(), phc)).isFalse();
    }

    @Test
    void malformedOrNonArgon2idPhcFailsClosed() {
        assertThat(verifier.verify("secret-value".toCharArray(), "$2a$malformed"))
                .isFalse();
        assertThat(verifier.verify("secret-value".toCharArray(), "$argon2id$malformed"))
                .isFalse();
    }
}
