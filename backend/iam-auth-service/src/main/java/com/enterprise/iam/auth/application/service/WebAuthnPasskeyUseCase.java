package com.enterprise.iam.auth.application.service;

import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.port.out.WebAuthnRepository;
import com.enterprise.iam.auth.domain.model.WebAuthnCeremonyType;
import com.enterprise.iam.auth.domain.model.WebAuthnChallenge;
import com.enterprise.iam.auth.domain.model.WebAuthnCredential;
import com.enterprise.iam.auth.domain.model.WebAuthnCredentialStatus;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class WebAuthnPasskeyUseCase {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final String RP_NAME = "Enterprise IAM";
    private static final String DEFAULT_RP_ID = "localhost";
    private static final long TIMEOUT_MS = 300_000L;
    private static final List<Integer> PUB_KEY_CRED_PARAMS = List.of(-7, -257);

    private final WebAuthnRepository repository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public WebAuthnPasskeyUseCase(
            WebAuthnRepository repository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this(repository, idGenerator, clock, new SecureRandom());
    }

    public WebAuthnPasskeyUseCase(
            WebAuthnRepository repository,
            PositiveIdGenerator idGenerator,
            Clock clock,
            SecureRandom secureRandom) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public RegistrationOptions createRegistrationOptions(
            long tenantId,
            long userId,
            String username,
            String rpId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        String resolvedRpId = (rpId == null || rpId.isBlank()) ? DEFAULT_RP_ID : rpId.trim();
        String challengeId = UUID.randomUUID().toString();
        String challengeB64 = generateChallengeBase64Url();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(CHALLENGE_TTL);

        WebAuthnChallenge challenge = new WebAuthnChallenge(
                challengeId,
                tenantId,
                userId,
                username.trim(),
                WebAuthnCeremonyType.REGISTRATION,
                challengeB64,
                resolvedRpId,
                "PENDING",
                expiresAt,
                now);
        repository.saveChallenge(challenge);

        return new RegistrationOptions(
                challengeId,
                challengeB64,
                resolvedRpId,
                RP_NAME,
                Long.toUnsignedString(userId),
                username.trim(),
                PUB_KEY_CRED_PARAMS,
                TIMEOUT_MS);
    }

    public WebAuthnCredential verifyRegistration(
            long tenantId,
            long userId,
            String challengeId,
            String credentialId,
            String publicKeyCose,
            long initialSignCount,
            String transports,
            String deviceName) {
        return verifyRegistration(
                tenantId,
                userId,
                challengeId,
                credentialId,
                publicKeyCose,
                initialSignCount,
                null,
                transports,
                deviceName);
    }

    public WebAuthnCredential verifyRegistration(
            long tenantId,
            long userId,
            String challengeId,
            String credentialId,
            String publicKeyCose,
            long initialSignCount,
            String aaguid,
            String transports,
            String deviceName) {
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("challengeId must not be blank");
        }
        if (credentialId == null || credentialId.isBlank()) {
            throw new IllegalArgumentException("credentialId must not be blank");
        }
        if (publicKeyCose == null || publicKeyCose.isBlank()) {
            throw new IllegalArgumentException("publicKeyCose must not be blank");
        }
        Instant now = clock.instant();
        WebAuthnChallenge challenge = repository.findPendingChallenge(tenantId, challengeId.trim())
                .orElseThrow(() -> new SecurityException("IAM_WEBAUTHN_CHALLENGE_INVALID: challenge not found or already consumed"));

        if (challenge.ceremonyType() != WebAuthnCeremonyType.REGISTRATION || !challenge.isPendingAndValidAt(now)) {
            throw new SecurityException("IAM_WEBAUTHN_CHALLENGE_EXPIRED: registration challenge expired or invalid");
        }
        if (challenge.userId() != null && challenge.userId() != userId) {
            throw new SecurityException("IAM_WEBAUTHN_USER_MISMATCH: challenge userId mismatch");
        }
        if (!repository.markChallengeConsumed(tenantId, challenge.challengeId(), now)) {
            throw new SecurityException("IAM_WEBAUTHN_CHALLENGE_CONSUMED: challenge already consumed");
        }
        if (repository.findCredentialByTenantAndCredentialId(tenantId, credentialId.trim()).isPresent()) {
            throw new IllegalStateException("IAM_WEBAUTHN_CREDENTIAL_EXISTS: credential already registered");
        }

        String resolvedDeviceName = (deviceName == null || deviceName.isBlank())
                ? "Passkey Authenticator"
                : deviceName.trim();
        WebAuthnCredential credential = new WebAuthnCredential(
                idGenerator.nextId(),
                tenantId,
                userId,
                credentialId.trim(),
                publicKeyCose.trim(),
                Math.max(0L, initialSignCount),
                aaguid == null || aaguid.isBlank() ? "00000000-0000-0000-0000-000000000000" : aaguid.trim(),
                transports == null || transports.isBlank() ? "internal,hybrid" : transports.trim(),
                resolvedDeviceName,
                WebAuthnCredentialStatus.ACTIVE,
                now,
                now);
        return repository.saveCredential(credential);
    }

    public AuthenticationOptions createAuthenticationOptions(
            long tenantId,
            String username,
            String rpId) {
        return createAuthenticationOptions(tenantId, null, username, rpId);
    }

    public AuthenticationOptions createAuthenticationOptions(
            long tenantId,
            Long userId,
            String username,
            String rpId) {
        String resolvedRpId = (rpId == null || rpId.isBlank()) ? DEFAULT_RP_ID : rpId.trim();
        String challengeId = UUID.randomUUID().toString();
        String challengeB64 = generateChallengeBase64Url();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(CHALLENGE_TTL);

        WebAuthnChallenge challenge = new WebAuthnChallenge(
                challengeId,
                tenantId,
                userId,
                username == null || username.isBlank() ? null : username.trim(),
                WebAuthnCeremonyType.AUTHENTICATION,
                challengeB64,
                resolvedRpId,
                "PENDING",
                expiresAt,
                now);
        repository.saveChallenge(challenge);

        List<String> allowedCredentialIds = userId == null
                ? List.of()
                : repository.findCredentialsByUser(tenantId, userId).stream()
                        .filter(c -> c.status() == WebAuthnCredentialStatus.ACTIVE)
                        .map(WebAuthnCredential::credentialId)
                        .toList();

        return new AuthenticationOptions(
                challengeId,
                challengeB64,
                resolvedRpId,
                TIMEOUT_MS,
                "preferred",
                allowedCredentialIds);
    }

    public AuthenticationVerifyResult verifyAuthentication(
            long tenantId,
            String challengeId,
            String credentialId,
            long authenticatorSignCount,
            String clientDataJsonB64,
            String signatureB64) {
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("challengeId must not be blank");
        }
        if (credentialId == null || credentialId.isBlank()) {
            throw new IllegalArgumentException("credentialId must not be blank");
        }
        if (clientDataJsonB64 == null || clientDataJsonB64.isBlank()
                || signatureB64 == null || signatureB64.isBlank()) {
            throw new SecurityException("IAM_WEBAUTHN_ASSERTION_INVALID: missing assertion payload");
        }
        Instant now = clock.instant();
        WebAuthnChallenge challenge = repository.findPendingChallenge(tenantId, challengeId.trim())
                .orElseThrow(() -> new SecurityException("IAM_WEBAUTHN_CHALLENGE_INVALID: challenge not found or already consumed"));

        if (challenge.ceremonyType() != WebAuthnCeremonyType.AUTHENTICATION || !challenge.isPendingAndValidAt(now)) {
            throw new SecurityException("IAM_WEBAUTHN_CHALLENGE_EXPIRED: authentication challenge expired or invalid");
        }
        if (!repository.markChallengeConsumed(tenantId, challenge.challengeId(), now)) {
            throw new SecurityException("IAM_WEBAUTHN_CHALLENGE_CONSUMED: challenge already consumed");
        }

        WebAuthnCredential credential = repository.findCredentialByTenantAndCredentialId(tenantId, credentialId.trim())
                .orElseThrow(() -> new SecurityException("IAM_WEBAUTHN_CREDENTIAL_NOT_FOUND: passkey credential not registered"));
        if (credential.status() != WebAuthnCredentialStatus.ACTIVE) {
            throw new SecurityException("IAM_WEBAUTHN_CREDENTIAL_REVOKED: passkey credential is revoked");
        }

        if (credential.signCount() > 0 && authenticatorSignCount <= credential.signCount()) {
            throw new SecurityException("IAM_WEBAUTHN_CLONE_DETECTED: authenticator signCount regression detected");
        }

        long nextSignCount = Math.max(credential.signCount(), authenticatorSignCount);
        repository.updateCredentialSignCount(tenantId, credential.id(), nextSignCount, now);

        return new AuthenticationVerifyResult(
                true,
                Long.toUnsignedString(credential.tenantId()),
                Long.toUnsignedString(credential.userId()),
                credential.credentialId(),
                credential.deviceName(),
                nextSignCount,
                now);
    }

    public List<WebAuthnCredential> listCredentials(long tenantId, long userId) {
        return repository.findCredentialsByUser(tenantId, userId);
    }

    public void revokeCredential(long tenantId, long userId, long id) {
        boolean updated = repository.revokeCredential(tenantId, userId, id);
        if (!updated) {
            throw new IllegalArgumentException("Passkey credential not found or already revoked");
        }
    }

    private String generateChallengeBase64Url() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record RegistrationOptions(
            String challengeId,
            String challenge,
            String rpId,
            String rpName,
            String userId,
            String username,
            List<Integer> pubKeyCredParams,
            long timeout) {
    }

    public record AuthenticationOptions(
            String challengeId,
            String challenge,
            String rpId,
            long timeout,
            String userVerification,
            List<String> allowCredentials) {
    }

    public record AuthenticationVerifyResult(
            boolean authenticated,
            String tenantId,
            String userId,
            String credentialId,
            String deviceName,
            long signCount,
            Instant authenticatedAt) {
    }
}
