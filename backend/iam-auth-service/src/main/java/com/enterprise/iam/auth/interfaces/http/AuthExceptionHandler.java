package com.enterprise.iam.auth.interfaces.http;

import com.enterprise.iam.auth.infrastructure.persistence.SessionIssuanceException;
import com.enterprise.iam.auth.infrastructure.persistence.SessionLimitExceededException;
import com.enterprise.iam.auth.infrastructure.persistence.PasswordChangeException;
import com.enterprise.iam.auth.domain.exception.PasswordChangeRejectedException;
import com.enterprise.iam.auth.domain.exception.PasswordPolicyViolationException;
import com.enterprise.iam.auth.interfaces.http.dto.AuthResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(SessionLimitExceededException.class)
    ResponseEntity<AuthResponse<Void>> handleSessionLimit(SessionLimitExceededException exception) {
        return error(HttpStatus.CONFLICT, "IAM_AUTH_SESSION_LIMIT_REACHED");
    }

    @ExceptionHandler(SessionIssuanceException.class)
    ResponseEntity<AuthResponse<Void>> handleSessionIssuance(SessionIssuanceException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "IAM_AUTH_DEPENDENCY_UNAVAILABLE");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<AuthResponse<Void>> handleDataAccess(DataAccessException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "IAM_AUTH_DEPENDENCY_UNAVAILABLE");
    }

    @ExceptionHandler(PasswordChangeRejectedException.class)
    ResponseEntity<AuthResponse<Void>> handlePasswordChangeRejected(
            PasswordChangeRejectedException exception) {
        return error(HttpStatus.UNAUTHORIZED, "IAM_AUTH_PASSWORD_CHANGE_REJECTED");
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    ResponseEntity<AuthResponse<Void>> handlePasswordPolicyViolation(
            PasswordPolicyViolationException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "IAM_AUTH_PASSWORD_POLICY_VIOLATION");
    }

    @ExceptionHandler(PasswordChangeException.class)
    ResponseEntity<AuthResponse<Void>> handlePasswordChangeFailure(
            PasswordChangeException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "IAM_AUTH_DEPENDENCY_UNAVAILABLE");
    }

    private static ResponseEntity<AuthResponse<Void>> error(HttpStatus status, String code) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new AuthResponse<>(false, code, null, traceId));
    }
}
