package com.enterprise.iam.auth.application.service;

import com.enterprise.iam.auth.application.command.LoginCommand;
import com.enterprise.iam.auth.application.model.AuthenticationAttempt;
import com.enterprise.iam.auth.application.model.LoginFailureReason;
import com.enterprise.iam.auth.application.model.LoginResult;
import com.enterprise.iam.auth.application.port.out.AuthenticationAttemptSink;
import com.enterprise.iam.auth.application.port.out.CredentialRepository;
import com.enterprise.iam.auth.application.port.out.LoginIdentityDirectory;
import com.enterprise.iam.auth.application.port.out.LoginSessionIssuer;
import com.enterprise.iam.auth.application.port.out.PasswordVerifier;
import com.enterprise.iam.auth.domain.model.CredentialSnapshot;
import com.enterprise.iam.auth.domain.model.ResolvedLoginIdentity;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * SEC-AUTHN-001: all credential rejections share one public result while internal
 * telemetry retains a non-public failure reason.
 */
public final class AuthenticateLoginUseCase {

    private final LoginIdentityDirectory identityDirectory;
    private final CredentialRepository credentialRepository;
    private final PasswordVerifier passwordVerifier;
    private final LoginSessionIssuer sessionIssuer;
    private final AuthenticationAttemptSink attemptSink;
    private final Clock clock;

    public AuthenticateLoginUseCase(
            LoginIdentityDirectory identityDirectory,
            CredentialRepository credentialRepository,
            PasswordVerifier passwordVerifier,
            LoginSessionIssuer sessionIssuer,
            AuthenticationAttemptSink attemptSink,
            Clock clock) {
        this.identityDirectory = Objects.requireNonNull(identityDirectory);
        this.credentialRepository = Objects.requireNonNull(credentialRepository);
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier);
        this.sessionIssuer = Objects.requireNonNull(sessionIssuer);
        this.attemptSink = Objects.requireNonNull(attemptSink);
        this.clock = Objects.requireNonNull(clock);
    }

    public LoginResult authenticate(LoginCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        char[] rawPassword = command.passwordCopy();
        try {
            Optional<ResolvedLoginIdentity> identity = identityDirectory.findByTenantAndIdentity(
                    command.tenantCode(), command.identityType(), command.identity());
            Optional<CredentialSnapshot> credential;
            if (identity.isPresent()) {
                credential = findMatchingCredential(identity.orElseThrow());
            } else {
                credentialRepository.performEnumerationResistantDummyLookup(command.tenantCode());
                credential = Optional.empty();
            }

            // Exactly one DB credential path and one expensive Argon2id path are executed.
            boolean passwordMatches = credential
                    .map(snapshot -> passwordVerifier.verify(rawPassword, snapshot.passwordPhc()))
                    .orElseGet(() -> passwordVerifier.verifyAgainstDummy(rawPassword));

            LoginFailureReason failure = determineFailure(identity, credential, passwordMatches, clock.instant());
            if (failure != null) {
                recordFailure(command, identity, failure);
                return LoginResult.rejected();
            }

            ResolvedLoginIdentity resolved = identity.orElseThrow();
            var session = sessionIssuer.issue(resolved, command.requestId());
            attemptSink.record(new AuthenticationAttempt(
                    true, resolved.tenantId(), resolved.userId(), null, command.requestId()));
            return LoginResult.authenticated(session);
        } finally {
            Arrays.fill(rawPassword, '\0');
            command.destroy();
        }
    }

    private Optional<CredentialSnapshot> findMatchingCredential(ResolvedLoginIdentity identity) {
        return credentialRepository.findByTenantAndUser(identity.tenantId(), identity.userId())
                .filter(snapshot -> snapshot.tenantId() == identity.tenantId())
                .filter(snapshot -> snapshot.userId() == identity.userId());
    }

    private static LoginFailureReason determineFailure(
            Optional<ResolvedLoginIdentity> identity,
            Optional<CredentialSnapshot> credential,
            boolean passwordMatches,
            Instant now) {
        if (identity.isEmpty()) {
            return LoginFailureReason.UNKNOWN_IDENTITY;
        }
        if (!identity.orElseThrow().active()) {
            return LoginFailureReason.INACTIVE_IDENTITY;
        }
        if (credential.isEmpty()) {
            return LoginFailureReason.MISSING_CREDENTIAL;
        }
        CredentialSnapshot snapshot = credential.orElseThrow();
        if (!snapshot.active()) {
            return LoginFailureReason.INACTIVE_CREDENTIAL;
        }
        if (snapshot.lockedAt(now)) {
            return LoginFailureReason.LOCKED_CREDENTIAL;
        }
        return passwordMatches ? null : LoginFailureReason.WRONG_PASSWORD;
    }

    private void recordFailure(
            LoginCommand command,
            Optional<ResolvedLoginIdentity> identity,
            LoginFailureReason failure) {
        attemptSink.record(new AuthenticationAttempt(
                false,
                identity.map(ResolvedLoginIdentity::tenantId).orElse(null),
                identity.map(ResolvedLoginIdentity::userId).orElse(null),
                failure,
                command.requestId()));
    }
}
