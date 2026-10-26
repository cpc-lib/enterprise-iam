package com.enterprise.iam.auth.interfaces.http;

import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.application.model.LoginResult;
import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.command.ChangePasswordCommand;
import com.enterprise.iam.auth.application.port.in.ChangePasswordUseCase;
import com.enterprise.iam.auth.application.port.out.LoginDeliveryConfirmer;
import com.enterprise.iam.auth.application.port.out.RefreshTokenRotator;
import com.enterprise.iam.auth.application.port.out.SessionRevoker;
import com.enterprise.iam.auth.application.service.AuthenticateLoginUseCase;
import com.enterprise.iam.auth.domain.model.IdentityType;
import com.enterprise.iam.auth.domain.exception.PasswordChangeRejectedException;
import com.enterprise.iam.auth.domain.exception.PasswordPolicyViolationException;
import com.enterprise.iam.auth.infrastructure.persistence.PasswordChangeException;
import com.enterprise.iam.auth.infrastructure.persistence.SessionIssuanceException;
import com.enterprise.iam.auth.interfaces.http.dto.AuthResponse;
import com.enterprise.iam.auth.interfaces.http.dto.ChangePasswordRequest;
import com.enterprise.iam.auth.interfaces.http.dto.LoginDeliveryConfirmationRequest;
import com.enterprise.iam.auth.interfaces.http.dto.LoginRequest;
import com.enterprise.iam.auth.interfaces.http.dto.LoginResponse;
import com.enterprise.iam.common.security.delegation.TrustedRequestContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private static final String RAW_REFRESH = "rt1.key." + "a".repeat(43);

    private AuthenticateLoginUseCase authenticateLoginUseCase;
    private LoginDeliveryConfirmer loginDeliveryConfirmer;
    private ChangePasswordUseCase changePasswordUseCase;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authenticateLoginUseCase = mock(AuthenticateLoginUseCase.class);
        loginDeliveryConfirmer = mock(LoginDeliveryConfirmer.class);
        changePasswordUseCase = mock(ChangePasswordUseCase.class);
        controller = new AuthController(
                authenticateLoginUseCase,
                mock(RefreshTokenRotator.class),
                mock(SessionRevoker.class),
                loginDeliveryConfirmer,
                changePasswordUseCase);
    }

    @Test
    void loginUsesFrozenSessionCookieAndDestroysIssuedRefreshBuffer() {
        SensitiveRefreshToken refreshToken =
                new SensitiveRefreshToken(RAW_REFRESH.toCharArray());
        IssuedLoginSession session = new IssuedLoginSession(
                "header.payload.signature",
                300L,
                refreshToken,
                3600L,
                101L,
                11L,
                7L);
        when(authenticateLoginUseCase.authenticate(any()))
                .thenReturn(LoginResult.authenticated(session));

        ResponseEntity<AuthResponse<LoginResponse>> response = controller.login(
                new LoginRequest("acme", IdentityType.USERNAME, "alice", "password", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isEqualTo(
                "IAM_REFRESH=" + RAW_REFRESH
                        + "; Path=/api/v1/auth; Secure; HttpOnly; SameSite=Strict");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().sessionId()).isEqualTo(101L);
        assertThat(refreshToken.isDestroyed()).isTrue();
    }

    @Test
    void deliveryConfirmationUsesCookieAndDestroysTemporarySecret() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("IAM_REFRESH", RAW_REFRESH)
        });
        AtomicReference<SensitiveRefreshToken> captured = new AtomicReference<>();
        when(loginDeliveryConfirmer.confirm(eq(101L), any())).thenAnswer(invocation -> {
            SensitiveRefreshToken token = invocation.getArgument(1);
            assertThat(token.isDestroyed()).isFalse();
            captured.set(token);
            return true;
        });

        ResponseEntity<?> response = controller.confirmLoginDelivery(
                new LoginDeliveryConfirmationRequest(101L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().isDestroyed()).isTrue();
    }

    @Test
    void deliveryConfirmationWithoutRefreshCookieFailsClosed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);

        ResponseEntity<?> response = controller.confirmLoginDelivery(
                new LoginDeliveryConfirmationRequest(101L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(loginDeliveryConfirmer, never()).confirm(eq(101L), any());
    }

    @Test
    void passwordChangeUsesRefreshCookieAndDestroysEveryTemporarySecret() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("IAM_REFRESH", RAW_REFRESH)
        });
        when(request.getAttribute(TrustedRequestContext.class.getName()))
                .thenReturn(trustedContext());
        AtomicReference<ChangePasswordCommand> captured = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            ChangePasswordCommand command = invocation.getArgument(0);
            assertThat(command.isDestroyed()).isFalse();
            captured.set(command);
            return null;
        }).when(changePasswordUseCase).changePassword(any());

        ResponseEntity<?> response = controller.changePassword(
                new ChangePasswordRequest(
                        "current-password-value", "replacement-password-value"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().expectedTenantId()).isEqualTo(7L);
        assertThat(captured.get().expectedUserId()).isEqualTo(11L);
        assertThat(captured.get().expectedSessionId()).isEqualTo(101L);
        assertThat(captured.get().isDestroyed()).isTrue();
    }

    @Test
    void passwordChangeWithoutRefreshCookieFailsClosed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);

        ResponseEntity<?> response = controller.changePassword(
                new ChangePasswordRequest(
                        "current-password-value", "replacement-password-value"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isInstanceOf(AuthResponse.class);
        assertThat(((AuthResponse<?>) response.getBody()).code())
                .isEqualTo("IAM_AUTH_PASSWORD_CHANGE_REJECTED");
        verify(changePasswordUseCase, never()).changePassword(any());
    }

    @Test
    void passwordChangeWithoutTrustedGatewayContextFailsClosed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("IAM_REFRESH", RAW_REFRESH)
        });

        ResponseEntity<?> response = controller.changePassword(
                new ChangePasswordRequest(
                        "current-password-value", "replacement-password-value"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(changePasswordUseCase, never()).changePassword(any());
    }

    @Test
    void passwordChangeRequestNeverRendersCredentialValues() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "current-secret-value", "replacement-secret-value");

        assertThat(request.toString()).doesNotContain("current-secret-value");
        assertThat(request.toString()).doesNotContain("replacement-secret-value");
        assertThat(request.toString()).contains("REDACTED");
    }

    @Test
    void passwordChangeFailuresUseFrozenStatusAndCodes() {
        AuthExceptionHandler handler = new AuthExceptionHandler();

        ResponseEntity<AuthResponse<Void>> rejected =
                handler.handlePasswordChangeRejected(new PasswordChangeRejectedException());
        ResponseEntity<AuthResponse<Void>> policy =
                handler.handlePasswordPolicyViolation(new PasswordPolicyViolationException());
        ResponseEntity<AuthResponse<Void>> dependency =
                handler.handlePasswordChangeFailure(new PasswordChangeException("outbox unavailable"));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rejected.getBody()).isNotNull();
        assertThat(rejected.getBody().code()).isEqualTo("IAM_AUTH_PASSWORD_CHANGE_REJECTED");
        assertThat(policy.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(policy.getBody()).isNotNull();
        assertThat(policy.getBody().code()).isEqualTo("IAM_AUTH_PASSWORD_POLICY_VIOLATION");
        assertThat(dependency.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(dependency.getBody()).isNotNull();
        assertThat(dependency.getBody().code()).isEqualTo("IAM_AUTH_DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void dependencyFailuresUseFrozenServiceUnavailableEnvelope() {
        AuthExceptionHandler handler = new AuthExceptionHandler();

        ResponseEntity<AuthResponse<Void>> databaseFailure = handler.handleDataAccess(
                new DataAccessResourceFailureException("database unavailable"));
        ResponseEntity<AuthResponse<Void>> issuanceFailure = handler.handleSessionIssuance(
                new SessionIssuanceException("signer unavailable"));

        assertThat(databaseFailure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(databaseFailure.getBody()).isNotNull();
        assertThat(databaseFailure.getBody().code())
                .isEqualTo("IAM_AUTH_DEPENDENCY_UNAVAILABLE");
        assertThat(issuanceFailure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(issuanceFailure.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    private static TrustedRequestContext trustedContext() {
        return new TrustedRequestContext(
                7L,
                11L,
                101L,
                "delegation-token-0001",
                "request-0001",
                Instant.parse("2026-10-11T05:00:00Z"));
    }
}
