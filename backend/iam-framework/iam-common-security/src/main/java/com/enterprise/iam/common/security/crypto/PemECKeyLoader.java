package com.enterprise.iam.common.security.crypto;

import com.enterprise.iam.common.security.jwt.P256Keys;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Dependency-free PKCS#8 EC private-key loading and JWK export for the ES256
 * profiles. Infrastructure-side utility only: production wiring must call this
 * from a fail-safe {@code @Bean} method, never generate key material silently.
 */
public final class PemECKeyLoader {

    private static final String PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_FOOTER = "-----END PRIVATE KEY-----";
    private static final int P_256_COORDINATE_BYTES = 32;

    private PemECKeyLoader() {
    }

    /** Parses a PKCS#8 {@code BEGIN PRIVATE KEY} PEM (inline, {@code \n} escapes supported) as an EC P-256 key. */
    public static ECPrivateKey parseP256PrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("PEM private key is blank");
        }
        String normalized = pem.replace("\\n", "\n").trim();
        int headerIndex = normalized.indexOf(PKCS8_HEADER);
        int footerIndex = normalized.indexOf(PKCS8_FOOTER);
        if (headerIndex < 0 || footerIndex < 0 || footerIndex < headerIndex) {
            throw new IllegalArgumentException(
                    "PEM must be PKCS#8 encoded (" + PKCS8_HEADER + " ... " + PKCS8_FOOTER + ")");
        }
        String body = normalized.substring(
                headerIndex + PKCS8_HEADER.length(), footerIndex);
        StringBuilder base64 = new StringBuilder(body.length());
        for (int index = 0; index < body.length(); index++) {
            char character = body.charAt(index);
            if (!Character.isWhitespace(character)) {
                base64.append(character);
            }
        }
        final byte[] encoded;
        try {
            encoded = Base64.getMimeDecoder().decode(base64.toString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("PEM private key body is not valid Base64", exception);
        }
        try {
            return requireP256((ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded)));
        } catch (ClassCastException exception) {
            throw new IllegalArgumentException("PEM private key is not an EC key", exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("PEM private key cannot be parsed", exception);
        }
    }

    /** Reads and parses a PKCS#8 PEM file from the given path. */
    public static ECPrivateKey parseP256PrivateKey(Path pemFile) {
        Objects.requireNonNull(pemFile, "pemFile must not be null");
        final String pem;
        try {
            pem = Files.readString(pemFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("PEM private key file cannot be read: " + pemFile, exception);
        }
        return parseP256PrivateKey(pem);
    }

    /** Derives the matching {@link ECPublicKey} (needed for JWK export) from the private scalar. */
    public static ECPublicKey derivePublicKey(ECPrivateKey privateKey) {
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        requireP256(privateKey);
        ECParameterSpec params = privateKey.getParams();
        ECPoint publicPoint = multiplyGenerator(
                params.getGenerator(), privateKey.getS(), params);
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(publicPoint, params));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("derived P-256 public key cannot be constructed", exception);
        }
    }

    /**
     * Exports the public key as a standard JWK map:
     * {@code {kty:EC, crv:P-256, x, y, kid, alg:ES256, use:sig}} (base64url without padding).
     */
    public static Map<String, Object> exportPublicJwk(ECPublicKey publicKey, String keyId) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        if (!P256Keys.isP256(publicKey)) {
            throw new IllegalArgumentException("JWK export requires a P-256 public key");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put("x", base64UrlCoordinate(publicKey.getW().getAffineX()));
        jwk.put("y", base64UrlCoordinate(publicKey.getW().getAffineY()));
        jwk.put("kid", keyId);
        jwk.put("alg", "ES256");
        jwk.put("use", "sig");
        return Collections.unmodifiableMap(jwk);
    }

    private static ECPrivateKey requireP256(ECPrivateKey privateKey) {
        if (!P256Keys.isP256(privateKey)) {
            throw new IllegalArgumentException(
                    "PEM private key must be an EC P-256 (secp256r1) key for ES256");
        }
        return privateKey;
    }

    private static ECPoint multiplyGenerator(ECPoint generator, BigInteger scalar, ECParameterSpec params) {
        ECFieldFp field = (ECFieldFp) params.getCurve().getField();
        BigInteger prime = field.getP();
        BigInteger a = params.getCurve().getA();
        ECPoint result = null;
        ECPoint addend = generator;
        for (int index = 0, bits = scalar.bitLength(); index < bits; index++) {
            if (scalar.testBit(index)) {
                result = result == null ? addend : add(result, addend, a, prime);
            }
            addend = doublePoint(addend, a, prime);
        }
        if (result == null) {
            throw new IllegalArgumentException("PEM private key scalar is invalid");
        }
        return result;
    }

    private static ECPoint add(ECPoint first, ECPoint second, BigInteger a, BigInteger prime) {
        if (first.equals(second)) {
            return doublePoint(first, a, prime);
        }
        BigInteger lambda = second.getAffineY().subtract(first.getAffineY())
                .multiply(second.getAffineX().subtract(first.getAffineX()).modInverse(prime))
                .mod(prime);
        return affinePoint(lambda, first, second.getAffineX(), prime);
    }

    private static ECPoint doublePoint(ECPoint point, BigInteger a, BigInteger prime) {
        if (point.getAffineY().signum() == 0) {
            throw new IllegalArgumentException("PEM private key scalar is invalid");
        }
        BigInteger lambda = point.getAffineX().pow(2).multiply(BigInteger.valueOf(3)).add(a)
                .multiply(point.getAffineY().shiftLeft(1).modInverse(prime))
                .mod(prime);
        return affinePoint(lambda, point, point.getAffineX(), prime);
    }

    private static ECPoint affinePoint(
            BigInteger lambda, ECPoint base, BigInteger otherX, BigInteger prime) {
        BigInteger x = lambda.multiply(lambda).subtract(base.getAffineX()).subtract(otherX).mod(prime);
        BigInteger y = lambda.multiply(base.getAffineX().subtract(x)).subtract(base.getAffineY()).mod(prime);
        return new ECPoint(x, y);
    }

    private static String base64UrlCoordinate(BigInteger coordinate) {
        byte[] raw = coordinate.toByteArray();
        byte[] unsigned = new byte[P_256_COORDINATE_BYTES];
        int length = Math.min(raw.length, P_256_COORDINATE_BYTES);
        System.arraycopy(raw, raw.length - length, unsigned, P_256_COORDINATE_BYTES - length, length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned);
    }
}
