package com.enterprise.iam.auth.infrastructure.config;

import com.enterprise.iam.auth.infrastructure.security.RefreshTokenHashKey;
import com.enterprise.iam.common.security.access.AccessTokenSigner;
import com.enterprise.iam.common.security.access.Es256AccessTokenSigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoKeyConfigurationTest {

    private Path tempDir;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CryptoKeyConfiguration.class);

    @BeforeEach
    void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory(Path.of("target"), "crypto-key-test");
    }

    @AfterEach
    void deleteTempDir() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    void remainsDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(AccessTokenSigner.class);
            assertThat(context).doesNotHaveBean(RefreshTokenHashKey.class);
        });
    }

    @Test
    void failsClosedWithoutAnyKeyMaterial() {
        runner.withPropertyValues("iam.auth.session-issuance.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("IAM_AUTH_CRYPTO_ACCESS_TOKEN_PRIVATE_KEY_PEM");
                });
    }

    @Test
    void failsClosedWhenBothKeySourcesProvided() {
        runner.withPropertyValues(
                        "iam.auth.session-issuance.enabled=true",
                        "iam.auth.crypto.access-token-private-key-pem=-----BEGIN PRIVATE KEY-----\\nAAAA\\n-----END PRIVATE KEY-----",
                        "iam.auth.crypto.access-token-private-key-path=/tmp/ec.pem")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Set only one");
                });
    }

    @Test
    void failsClosedWithoutAccessTokenKid() {
        runner.withPropertyValues(
                        "iam.auth.session-issuance.enabled=true",
                        "iam.auth.crypto.access-token-private-key-pem=" + inlineEscapedPem(),
                        "iam.auth.crypto.refresh-token-hmac-secret=" + base64Secret())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("IAM_AUTH_CRYPTO_ACCESS_TOKEN_KID");
                });
    }

    @Test
    void failsClosedWithMissingOrShortHmacSecret() {
        runner.withPropertyValues(
                        "iam.auth.session-issuance.enabled=true",
                        "iam.auth.crypto.access-token-private-key-pem=" + inlineEscapedPem(),
                        "iam.auth.crypto.access-token-kid=auth-key-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("IAM_AUTH_CRYPTO_REFRESH_TOKEN_HMAC_SECRET");
                });
        runner.withPropertyValues(
                        "iam.auth.session-issuance.enabled=true",
                        "iam.auth.crypto.access-token-private-key-pem=" + inlineEscapedPem(),
                        "iam.auth.crypto.access-token-kid=auth-key-1",
                        "iam.auth.crypto.refresh-token-hmac-secret=too-short")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("at least 32 bytes");
                });
    }

    @Test
    void createsSignerPublicJwkAndHashKeyFromInlinePem() {
        runner.withPropertyValues(
                        "iam.auth.session-issuance.enabled=true",
                        "iam.auth.crypto.access-token-private-key-pem=" + inlineEscapedPem(),
                        "iam.auth.crypto.access-token-kid=auth-key-1",
                        "iam.auth.crypto.refresh-token-hmac-secret=" + base64Secret())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AccessTokenSigner.class))
                            .isInstanceOf(Es256AccessTokenSigner.class);
                    assertThat(context.getBean(RefreshTokenHashKey.class).keyId())
                            .isEqualTo("rt-1");
                    assertThat(context.getBean(AccessTokenPublicJwk.class).jwk())
                            .containsEntry("kid", "auth-key-1")
                            .containsEntry("kty", "EC")
                            .containsEntry("crv", "P-256")
                            .containsEntry("alg", "ES256")
                            .containsEntry("use", "sig");
                });
    }

    @Test
    void createsSignerFromKeyFilePath() throws Exception {
        Path pemFile = tempDir.resolve("ec.pkcs8.pem");
        Files.writeString(pemFile, inlinePem(), StandardCharsets.UTF_8);
        runner.withPropertyValues(
                        "iam.auth.session-issuance.enabled=true",
                        "iam.auth.crypto.access-token-private-key-path=" + pemFile,
                        "iam.auth.crypto.access-token-kid=auth-key-1",
                        "iam.auth.crypto.refresh-token-hmac-secret=" + base64Secret())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AccessTokenSigner.class);
                    assertThat(context.getBean(AccessTokenPublicJwk.class).jwk())
                            .containsEntry("kid", "auth-key-1");
                });
    }

    private static String inlinePem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                    .encodeToString(pair.getPrivate().getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String inlineEscapedPem() {
        return inlinePem().replace("\n", "\\n");
    }

    private static String base64Secret() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }
}
