package com.enterprise.iam.auth.interfaces.http;

import com.enterprise.iam.auth.application.command.LoginCommand;
import com.enterprise.iam.auth.application.command.RefreshRotationCommand;
import com.enterprise.iam.auth.application.command.ChangePasswordCommand;
import com.enterprise.iam.auth.application.model.IssuedLoginSession;
import com.enterprise.iam.auth.application.model.LoginResult;
import com.enterprise.iam.auth.application.model.RefreshRotationResult;
import com.enterprise.iam.auth.application.model.SensitiveRefreshToken;
import com.enterprise.iam.auth.application.port.out.RefreshTokenRotator;
import com.enterprise.iam.auth.application.port.in.ChangePasswordUseCase;
import com.enterprise.iam.auth.application.port.out.LoginDeliveryConfirmer;
import com.enterprise.iam.auth.application.port.out.SessionRevoker;
import com.enterprise.iam.auth.application.service.AuthenticateLoginUseCase;
import com.enterprise.iam.auth.domain.model.IdentityType;
import com.enterprise.iam.auth.interfaces.http.dto.AuthResponse;
import com.enterprise.iam.auth.interfaces.http.dto.ChangePasswordRequest;
import com.enterprise.iam.auth.interfaces.http.dto.LoginRequest;
import com.enterprise.iam.auth.interfaces.http.dto.LoginDeliveryConfirmationRequest;
import com.enterprise.iam.auth.interfaces.http.dto.LoginResponse;
import com.enterprise.iam.auth.interfaces.http.dto.RefreshResponse;
import com.enterprise.iam.common.security.delegation.TrustedRequestContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "iam.auth.http", name = "enabled", havingValue = "true")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "IAM_REFRESH";
    private static final String NO_STORE = "no-store";

    private final AuthenticateLoginUseCase authenticateLoginUseCase;
    private final RefreshTokenRotator refreshTokenRotator;
    private final SessionRevoker sessionRevoker;
    private final LoginDeliveryConfirmer loginDeliveryConfirmer;
    private final ChangePasswordUseCase changePasswordUseCase;

    public AuthController(
            AuthenticateLoginUseCase authenticateLoginUseCase,
            RefreshTokenRotator refreshTokenRotator,
            SessionRevoker sessionRevoker,
            LoginDeliveryConfirmer loginDeliveryConfirmer,
            ChangePasswordUseCase changePasswordUseCase) {
        this.authenticateLoginUseCase = authenticateLoginUseCase;
        this.refreshTokenRotator = refreshTokenRotator;
        this.sessionRevoker = sessionRevoker;
        this.loginDeliveryConfirmer = loginDeliveryConfirmer;
        this.changePasswordUseCase = changePasswordUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        String traceId = UUID.randomUUID().toString();
        char[] password = request.password().toCharArray();
        try {
            LoginCommand command = new LoginCommand(
                    request.tenantCode(),
                    IdentityType.valueOf(request.identityType().name()),
                    request.identity(),
                    password,
                    traceId);
            LoginResult result = authenticateLoginUseCase.authenticate(command);
            if (!result.authenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                        .header(HttpHeaders.PRAGMA, "no-cache")
                        .body(new AuthResponse<>(false, result.publicCode(), null, traceId));
            }

            IssuedLoginSession session = result.issuedSession().orElseThrow();
            SensitiveRefreshToken refreshToken = session.refreshToken();
            try {
                char[] refreshValue = refreshToken.copyValue();
                try {
                    ResponseCookie cookie = ResponseCookie.from(
                                    REFRESH_COOKIE_NAME, new String(refreshValue))
                            .path("/api/v1/auth")
                            .httpOnly(true)
                            .secure(true)
                            .sameSite("Strict")
                            .build();
                    LoginResponse response = LoginResponse.fromSession(
                            session.accessToken(),
                            session.expiresIn(),
                            session.sessionId(),
                            session.userId(),
                            session.tenantId());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                            .header(HttpHeaders.PRAGMA, "no-cache")
                            .body(AuthResponse.ok(response, traceId));
                } finally {
                    java.util.Arrays.fill(refreshValue, '\0');
                }
            } finally {
                refreshToken.destroy();
            }
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse<RefreshResponse>> refresh(HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(new AuthResponse<>(false, "IAM_AUTHENTICATION_FAILED", null, traceId));
        }
        char[] tokenChars = refreshToken.toCharArray();
        try {
            RefreshRotationCommand command = new RefreshRotationCommand(tokenChars, traceId);
            try {
                RefreshRotationResult result = refreshTokenRotator.rotate(command);
                if (!result.rotated()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                            .header(HttpHeaders.PRAGMA, "no-cache")
                            .body(new AuthResponse<>(false, result.publicCode(), null, traceId));
                }
                IssuedLoginSession session = result.session();
                SensitiveRefreshToken newRefreshToken = session.refreshToken();
                try {
                    char[] refreshValue = newRefreshToken.copyValue();
                    try {
                        ResponseCookie cookie = ResponseCookie.from(
                                        REFRESH_COOKIE_NAME, new String(refreshValue))
                                .path("/api/v1/auth")
                                .httpOnly(true)
                                .secure(true)
                                .sameSite("Strict")
                                .build();
                        RefreshResponse response = RefreshResponse.fromSession(
                                session.accessToken(),
                                session.expiresIn(),
                                session.sessionId());
                        return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                                .header(HttpHeaders.PRAGMA, "no-cache")
                                .body(AuthResponse.ok(response, traceId));
                    } finally {
                        java.util.Arrays.fill(refreshValue, '\0');
                    }
                } finally {
                    newRefreshToken.destroy();
                }
            } finally {
                command.destroy();
            }
        } finally {
            java.util.Arrays.fill(tokenChars, '\0');
        }
    }

    @PostMapping("/login-deliveries/confirm")
    public ResponseEntity<?> confirmLoginDelivery(
            @Valid @RequestBody LoginDeliveryConfirmationRequest request,
            HttpServletRequest servletRequest) {
        String traceId = UUID.randomUUID().toString();
        String refreshToken = extractRefreshCookie(servletRequest);
        if (refreshToken == null) {
            return deliveryConfirmationRejected(traceId);
        }
        char[] tokenChars = refreshToken.toCharArray();
        SensitiveRefreshToken sensitiveToken = null;
        try {
            try {
                sensitiveToken = new SensitiveRefreshToken(tokenChars);
            } catch (IllegalArgumentException exception) {
                return deliveryConfirmationRejected(traceId);
            }
            if (!loginDeliveryConfirmer.confirm(request.sessionId(), sensitiveToken)) {
                return deliveryConfirmationRejected(traceId);
            }
            return ResponseEntity.noContent()
                    .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .build();
        } finally {
            java.util.Arrays.fill(tokenChars, '\0');
            if (sensitiveToken != null) {
                sensitiveToken.destroy();
            }
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            @RequestHeader("X-Tenant-Id") long tenantId,
            @RequestHeader("X-Session-Id") long sessionId) {
        sessionRevoker.revokeSession(tenantId, sessionId);
        ResponseCookie clearCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .path("/api/v1/auth")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .maxAge(0)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .build();
    }

    @PostMapping("/password/change")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest) {
        TrustedRequestContext trustedContext = trustedContext(servletRequest);
        String traceId = trustedContext == null
                ? UUID.randomUUID().toString()
                : trustedContext.requestId();
        String refreshToken = extractRefreshCookie(servletRequest);
        if (trustedContext == null
                || refreshToken == null
                || request.currentPassword().isEmpty()) {
            return passwordChangeRejected(traceId);
        }
        if (request.newPassword().isEmpty()
                || request.newPassword().length() > 512) {
            return passwordPolicyViolation(traceId);
        }
        if (request.currentPassword().length() > 512) {
            return passwordChangeRejected(traceId);
        }

        char[] refreshChars = refreshToken.toCharArray();
        char[] currentPassword = request.currentPassword().toCharArray();
        char[] newPassword = request.newPassword().toCharArray();
        ChangePasswordCommand command = null;
        try {
            try {
                command = new ChangePasswordCommand(
                        refreshChars,
                        currentPassword,
                        newPassword,
                        trustedContext.tenantId(),
                        trustedContext.subjectId(),
                        trustedContext.sessionId(),
                        traceId);
            } catch (IllegalArgumentException exception) {
                return passwordChangeRejected(traceId);
            }
            changePasswordUseCase.changePassword(command);
            return ResponseEntity.noContent()
                    .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .build();
        } finally {
            java.util.Arrays.fill(refreshChars, '\0');
            java.util.Arrays.fill(currentPassword, '\0');
            java.util.Arrays.fill(newPassword, '\0');
            if (command != null) {
                command.destroy();
            }
        }
    }

    private ResponseEntity<AuthResponse<Void>> deliveryConfirmationRejected(String traceId) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new AuthResponse<>(
                        false, "IAM_AUTH_LOGIN_DELIVERY_INVALID", null, traceId));
    }

    private ResponseEntity<AuthResponse<Void>> passwordChangeRejected(String traceId) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new AuthResponse<>(
                        false, "IAM_AUTH_PASSWORD_CHANGE_REJECTED", null, traceId));
    }

    private ResponseEntity<AuthResponse<Void>> passwordPolicyViolation(String traceId) {
        return ResponseEntity.unprocessableEntity()
                .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new AuthResponse<>(
                        false, "IAM_AUTH_PASSWORD_POLICY_VIOLATION", null, traceId));
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private TrustedRequestContext trustedContext(HttpServletRequest request) {
        Object value = request.getAttribute(TrustedRequestContext.class.getName());
        return value instanceof TrustedRequestContext context ? context : null;
    }
}
