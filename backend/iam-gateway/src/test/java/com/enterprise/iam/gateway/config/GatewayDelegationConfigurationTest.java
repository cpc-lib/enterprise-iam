package com.enterprise.iam.gateway.config;

import com.enterprise.iam.common.security.delegation.Es256DelegationTokenSigner;
import com.enterprise.iam.gateway.delegation.GatewayDelegationTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayDelegationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayDelegationConfiguration.class);

    @Test
    void remainsDisabledByDefault() {
        runner.run(context ->
                assertThat(context).doesNotHaveBean(GatewayDelegationTokenIssuer.class));
    }

    @Test
    void failsClosedWithoutKeyMaterial() {
        runner.withPropertyValues(
                        "iam.gateway.delegation.enabled=true",
                        "iam.gateway.delegation.route-audiences.internal-route=iam-authorization-service")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("IAM_GATEWAY_DELEGATION_PRIVATE_KEY_PEM");
                });
    }

    @Test
    void failsClosedWithoutDelegationKeyId() {
        runner.withPropertyValues(
                        "iam.gateway.delegation.enabled=true",
                        "iam.gateway.delegation.route-audiences.internal-route=iam-authorization-service",
                        "iam.gateway.delegation.private-key-pem=" + inlineEscapedPem())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("IAM_GATEWAY_DELEGATION_KEY_ID");
                });
    }

    @Test
    void createsSignerPublicJwkAndIssuerFromKeyMaterial() {
        runner.withPropertyValues(
                        "iam.gateway.delegation.enabled=true",
                        "iam.gateway.delegation.route-audiences.internal-route=iam-authorization-service",
                        "iam.gateway.delegation.private-key-pem=" + inlineEscapedPem(),
                        "iam.gateway.delegation.delegation-key-id=delegation-key-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Es256DelegationTokenSigner.class);
                    assertThat(context).hasSingleBean(GatewayDelegationTokenIssuer.class);
                    assertThat(context.getBean(DelegationPublicJwk.class).jwk())
                            .containsEntry("kid", "delegation-key-1")
                            .containsEntry("kty", "EC")
                            .containsEntry("crv", "P-256")
                            .containsEntry("alg", "ES256")
                            .containsEntry("use", "sig");
                });
    }

    @Test
    void failsClosedWithInvalidPem() {
        runner.withPropertyValues(
                        "iam.gateway.delegation.enabled=true",
                        "iam.gateway.delegation.route-audiences.internal-route=iam-authorization-service",
                        "iam.gateway.delegation.private-key-pem=-----BEGIN PRIVATE KEY-----\\nAAAA\\n-----END PRIVATE KEY-----",
                        "iam.gateway.delegation.delegation-key-id=delegation-key-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("private key is invalid");
                });
    }

    private static String inlineEscapedPem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                    .encodeToString(pair.getPrivate().getEncoded());
            return ("-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n")
                    .replace("\n", "\\n");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
