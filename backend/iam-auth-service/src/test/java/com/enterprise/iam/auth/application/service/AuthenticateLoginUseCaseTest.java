package com.enterprise.iam.auth.application.service;

import com.enterprise.iam.auth.application.command.LoginCommand;
import com.enterprise.iam.auth.application.model.AuthenticationAttempt;
import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.application.model.LoginFailureReason;
import com.enterprise.iam.auth.application.model.LoginResult;
import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.port.out.AuthenticationAttemptSink;
import com.enterprise.iam.auth.application.port.out.CredentialRepository;
import com.enterprise.iam.auth.application.port.out.PasswordVerifier;
import com.enterprise.iam.auth.domain.model.CredentialSnapshot;
import com.enterprise.iam.auth.domain.model.IdentityType;
import com.enterprise.iam.auth.domain.model.ResolvedLoginIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticateLoginUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private static final ResolvedLoginIdentity ACTIVE_IDENTITY =
            new ResolvedLoginIdentity(10, 20, true);
    private static final CredentialSnapshot ACTIVE_CREDENTIAL =
            new CredentialSnapshot(10, 20, "$argon2id$test", true, null);

    @Test
    @DisplayName("SEC-AUTHN-001 unknown identity and wrong password expose the same outcome and bounded work")
    void unknownIdentityAndWrongPasswordHaveSamePublicOutcomeAndBoundedWork() {
        Fixture unknown = new Fixture(Optional.empty(), Optional.empty(), false);
        LoginCommand unknownCommand = command();
        LoginResult unknownResult = unknown.useCase.authenticate(unknownCommand);

        Fixture wrong = new Fixture(Optional.of(ACTIVE_IDENTITY), Optional.of(ACTIVE_CREDENTIAL), false);
        LoginCommand wrongCommand = command();
        LoginResult wrongResult = wrong.useCase.authenticate(wrongCommand);

        assertThat(unknownResult).isEqualTo(LoginResult.rejected()).isEqualTo(wrongResult);
        assertThat(unknownResult.publicCode()).isEqualTo("IAM_AUTHENTICATION_FAILED");
        assertThat(unknownResult.issuedSession()).isEmpty();
        assertThat(unknown.verifier.totalCalls()).isEqualTo(1);
        assertThat(wrong.verifier.totalCalls()).isEqualTo(1);
        assertThat(unknown.credentialRepository.totalLookupPaths()).isEqualTo(1);
        assertThat(wrong.credentialRepository.totalLookupPaths()).isEqualTo(1);
        assertThat(unknownCommand.isDestroyed()).isTrue();
        assertThat(wrongCommand.isDestroyed()).isTrue();
        assertThatThrownBy(unknownCommand::passwordCopy)
                .isInstanceOf(IllegalStateException.class);
        assertThat(unknown.attempts).singleElement()
                .extracting(AuthenticationAttempt::failureReason)
                .isEqualTo(LoginFailureReason.UNKNOWN_IDENTITY);
        assertThat(wrong.attempts).singleElement()
                .extracting(AuthenticationAttempt::failureReason)
                .isEqualTo(LoginFailureReason.WRONG_PASSWORD);
    }

    @Test
    void inactiveIdentityStillRunsRealPasswordVerificationAndCannotReceiveSession() {
        ResolvedLoginIdentity inactive = new ResolvedLoginIdentity(10, 20, false);
        Fixture fixture = new Fixture(Optional.of(inactive), Optional.of(ACTIVE_CREDENTIAL), true);

        LoginResult result = fixture.useCase.authenticate(command());

        assertThat(result).isEqualTo(LoginResult.rejected());
        assertThat(fixture.verifier.realCalls).isEqualTo(1);
        assertThat(fixture.sessionIssueCount).isZero();
        assertThat(fixture.attempts).singleElement()
                .extracting(AuthenticationAttempt::failureReason)
                .isEqualTo(LoginFailureReason.INACTIVE_IDENTITY);
    }

    @Test
    void crossTenantCredentialIsNotTrustedAndFallsBackToDummyPasswordPath() {
        CredentialSnapshot mismatched =
                new CredentialSnapshot(99, 20, "$argon2id$untrusted", true, null);
        Fixture fixture = new Fixture(
                Optional.of(ACTIVE_IDENTITY), Optional.of(mismatched), true);

        LoginResult result = fixture.useCase.authenticate(command());

        assertThat(result).isEqualTo(LoginResult.rejected());
        assertThat(fixture.verifier.realCalls).isZero();
        assertThat(fixture.verifier.dummyCalls).isEqualTo(1);
        assertThat(fixture.sessionIssueCount).isZero();
    }

    @Test
    void issuesSessionOnlyAfterIdentityCredentialLockAndPasswordChecksPass() {
        Fixture fixture = new Fixture(
                Optional.of(ACTIVE_IDENTITY), Optional.of(ACTIVE_CREDENTIAL), true);

        LoginResult result = fixture.useCase.authenticate(command());

        assertThat(result.authenticated()).isTrue();
        assertThat(result.publicCode()).isEqualTo("OK");
        assertThat(result.issuedSession()).get().satisfies(session -> {
            assertThat(session.tenantId()).isEqualTo(10);
            assertThat(session.userId()).isEqualTo(20);
        });
        assertThat(fixture.sessionIssueCount).isEqualTo(1);
        assertThat(fixture.attempts).singleElement()
                .satisfies(attempt -> {
                    assertThat(attempt.successful()).isTrue();
                    assertThat(attempt.failureReason()).isNull();
                });
    }

    private static LoginCommand command() {
        return new LoginCommand(
                "acme", IdentityType.USERNAME, "alice", "secret-value".toCharArray(), "request-0001");
    }

    private static final class Fixture {
        private final TrackingCredentialRepository credentialRepository;
        private final TrackingPasswordVerifier verifier;
        private final List<AuthenticationAttempt> attempts = new ArrayList<>();
        private final AuthenticateLoginUseCase useCase;
        private int sessionIssueCount;

        private Fixture(
                Optional<ResolvedLoginIdentity> identity,
                Optional<CredentialSnapshot> credential,
                boolean passwordMatches) {
            this.credentialRepository = new TrackingCredentialRepository(credential);
            this.verifier = new TrackingPasswordVerifier(passwordMatches);
            AuthenticationAttemptSink sink = attempts::add;
            this.useCase = new AuthenticateLoginUseCase(
                    (tenantCode, identityType, normalizedIdentity) -> identity,
                    credentialRepository,
                    verifier,
                    (resolved, requestId) -> {
                        sessionIssueCount++;
                        return new IssuedLoginSession(
                                "access-token",
                                300,
                                new SensitiveRefreshToken(
                                        "rt1.test-key.0123456789012345678901234567890123456789012"
                                                .toCharArray()),
                                1_209_600,
                                30,
                                resolved.userId(),
                                resolved.tenantId());
                    },
                    sink,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    private static final class TrackingCredentialRepository implements CredentialRepository {
        private final Optional<CredentialSnapshot> credential;
        private int realLookups;
        private int dummyLookups;

        private TrackingCredentialRepository(Optional<CredentialSnapshot> credential) {
            this.credential = credential;
        }

        @Override
        public Optional<CredentialSnapshot> findByTenantAndUser(long tenantId, long userId) {
            realLookups++;
            return credential;
        }

        @Override
        public void performEnumerationResistantDummyLookup(String tenantCode) {
            dummyLookups++;
        }

        private int totalLookupPaths() {
            return realLookups + dummyLookups;
        }
    }

    private static final class TrackingPasswordVerifier implements PasswordVerifier {
        private final boolean passwordMatches;
        private int realCalls;
        private int dummyCalls;

        private TrackingPasswordVerifier(boolean passwordMatches) {
            this.passwordMatches = passwordMatches;
        }

        @Override
        public boolean verify(char[] rawPassword, String passwordPhc) {
            realCalls++;
            return passwordMatches;
        }

        @Override
        public boolean verifyAgainstDummy(char[] rawPassword) {
            dummyCalls++;
            return false;
        }

        private int totalCalls() {
            return realCalls + dummyCalls;
        }
    }
}
