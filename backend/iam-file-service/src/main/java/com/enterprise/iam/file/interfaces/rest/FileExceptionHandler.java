package com.enterprise.iam.file.interfaces.rest;

import com.enterprise.iam.file.application.exception.FileLockedException;
import com.enterprise.iam.file.application.exception.FileResourceException;
import com.enterprise.iam.file.application.exception.ResourceNotFoundException;
import com.enterprise.iam.file.application.exception.VersionConflictException;
import com.enterprise.iam.file.application.exception.DomainInvariantException;
import com.enterprise.iam.file.interfaces.rest.dto.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
@ConditionalOnProperty(prefix = "iam.file", name = "enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FileExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.code(), traceId));
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleConflict(VersionConflictException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.code(), traceId));
    }

    @ExceptionHandler(DomainInvariantException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvariant(DomainInvariantException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.code(), traceId));
    }

    @ExceptionHandler(FileLockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleLocked(FileLockedException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(ApiResponse.error(ex.code(), traceId));
    }

    @ExceptionHandler(FileResourceException.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(FileResourceException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.code(), traceId));
    }
}
