package com.enterprise.iam.sharing.interfaces.rest;

import com.enterprise.iam.sharing.application.exception.ShareDomainInvariantException;
import com.enterprise.iam.sharing.interfaces.rest.dto.ErrorResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Maps share security/domain invariant failures to the contract's
 * 422 Unprocessable response.
 */
@RestControllerAdvice
@ConditionalOnProperty(prefix = "iam.sharing", name = "enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShareExceptionHandler {

    @ExceptionHandler(ShareDomainInvariantException.class)
    public ResponseEntity<ErrorResponse> handleInvariant(ShareDomainInvariantException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.error(ShareDomainInvariantException.CODE, ex.getMessage(), traceId));
    }
}
