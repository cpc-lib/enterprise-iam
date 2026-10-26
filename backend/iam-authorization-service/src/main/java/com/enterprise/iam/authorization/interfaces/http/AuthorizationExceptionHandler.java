package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.service.DomainInvariantException;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class AuthorizationExceptionHandler {

    @ExceptionHandler(DomainInvariantException.class)
    public ResponseEntity<ApiResponse<Object>> handleDomainInvariant(DomainInvariantException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiResponse<>(false, "IAM_AUTHZ_INVARIANT_VIOLATION", null, traceId));
    }
}
