package com.enterprise.iam.common.security.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PemECKeyLoaderTest {

    private Path tempDir;

    @BeforeEach
    void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory(Path.of("target"), "pem-loader-test");
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
    void parsesPemAndDerivesMatchingPublicKey() throws Exception {
        KeyPair pair = generateP256();
        String pem = encodePkcs8Pem(pair.getPrivate(), true);

        ECPrivateKey parsed = PemECKeyLoader.parseP256PrivateKey(pem);
        ECPublicKey derived = PemECKeyLoader.derivePublicKey(parsed);

        assertThat(parsed).isInstanceOf(ECPrivateKey.class);
        assertThat(parsed.getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
        assertThat(derived.getW()).isEqualTo(((ECPublicKey) pair.getPublic()).getW());
        assertThat(derived.getEncoded()).isEqualTo(pair.getPublic().getEncoded());
    }

    @Test
    void parsesEscapedInlinePemAndLoadsFromFilePath() throws Exception {
        KeyPair pair = generateP256();
        String escaped = encodePkcs8Pem(pair.getPrivate(), true).replace("\n", "\\n");

        ECPrivateKey fromEscaped = PemECKeyLoader.parseP256PrivateKey(escaped);
        assertThat(fromEscaped.getEncoded()).isEqualTo(pair.getPrivate().getEncoded());

        Path pemFile = tempDir.resolve("ec.pkcs8.pem");
        Files.writeString(pemFile, encodePkcs8Pem(pair.getPrivate(), true));
        ECPrivateKey fromFile = PemECKeyLoader.parseP256PrivateKey(pemFile);
        assertThat(fromFile.getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
    }

    @Test
    void exportsStandardEcJwkFields() throws Exception {
        KeyPair pair = generateP256();
        ECPublicKey publicKey = (ECPublicKey) pair.getPublic();

        Map<String, Object> jwk = PemECKeyLoader.exportPublicJwk(publicKey, "auth-key-1");

        assertThat(jwk)
                .containsEntry("kty", "EC")
                .containsEntry("crv", "P-256")
                .containsEntry("kid", "auth-key-1")
                .containsEntry("alg", "ES256")
                .containsEntry("use", "sig");
        assertThat(jwk.get("x")).asString().doesNotContain("=", "+", "/");
        assertThat(jwk.get("y")).asString().doesNotContain("=", "+", "/");
        assertThat(Base64.getUrlDecoder().decode((String) jwk.get("x"))).hasSize(32);
        assertThat(Base64.getUrlDecoder().decode((String) jwk.get("y"))).hasSize(32);
        assertThat(jwk.get("x")).isEqualTo(PemECKeyLoader.exportPublicJwk(
                PemECKeyLoader.derivePublicKey((ECPrivateKey) pair.getPrivate()), "auth-key-1").get("x"));
    }

    @Test
    void rejectsNonP256AndMalformedPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair p384 = generator.generateKeyPair();

        assertThatThrownBy(() -> PemECKeyLoader.parseP256PrivateKey(
                encodePkcs8Pem(p384.getPrivate(), true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P-256");
        assertThatThrownBy(() -> PemECKeyLoader.parseP256PrivateKey(
                "-----BEGIN PRIVATE KEY-----\nbm90LWEta2V5\n-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PemECKeyLoader.parseP256PrivateKey("not a pem at all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PKCS#8");
        assertThatThrownBy(() -> PemECKeyLoader.parseP256PrivateKey("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    private static KeyPair generateP256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String encodePkcs8Pem(PrivateKey privateKey, boolean withNewlines) {
        byte[] pkcs8 = privateKey.getEncoded();
        String base64 = withNewlines
                ? Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs8)
                : Base64.getEncoder().encodeToString(pkcs8);
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }
}
