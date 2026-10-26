package com.enterprise.iam.auth.interfaces.http;

import com.enterprise.iam.auth.application.service.SessionQueryUseCase;
import com.enterprise.iam.auth.domain.model.ActiveSession;
import com.enterprise.iam.auth.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.auth.interfaces.http.dto.PageResponse;
import com.enterprise.iam.auth.interfaces.http.dto.RevokeSessionRequest;
import com.enterprise.iam.auth.interfaces.http.dto.SessionDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/sessions")
@ConditionalOnProperty(prefix = "iam.auth.http", name = "enabled", havingValue = "true")
public class AdminSessionController {

    private final SessionQueryUseCase sessionQueryUseCase;

    public AdminSessionController(SessionQueryUseCase sessionQueryUseCase) {
        this.sessionQueryUseCase = Objects.requireNonNull(sessionQueryUseCase);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SessionDto>>> listSessions(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        String traceId = UUID.randomUUID().toString();
        var result = sessionQueryUseCase.listSessions(tenantId, userId, status, page, pageSize);
        List<SessionDto> dtos = result.items().stream().map(this::toDto).toList();
        var pageResponse = new PageResponse<>(dtos, result.total(), result.page(), result.pageSize());
        return ResponseEntity.ok(ApiResponse.ok(pageResponse, traceId));
    }

    @PostMapping("/{sessionId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "1") long tenantId,
            @PathVariable long sessionId,
            @RequestBody(required = false) RevokeSessionRequest request) {
        String traceId = UUID.randomUUID().toString();
        String reason = (request != null) ? request.reason() : "ADMIN_MANUAL_REVOKE";
        sessionQueryUseCase.revokeSession(tenantId, sessionId, reason);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    private SessionDto toDto(ActiveSession s) {
        return new SessionDto(
                s.id(),
                s.tenantId(),
                s.sessionId(),
                s.userId(),
                s.status(),
                s.deviceType(),
                s.lastAccessAt(),
                s.idleExpireAt(),
                s.absoluteExpireAt(),
                s.lastStrongAuthAt(),
                s.revokedAt(),
                s.revokeReason(),
                s.sessionVersion(),
                s.createdAt(),
                s.updatedAt()
        );
    }
}
