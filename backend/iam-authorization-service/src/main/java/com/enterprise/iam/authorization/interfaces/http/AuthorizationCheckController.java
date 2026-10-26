package com.enterprise.iam.authorization.interfaces.http;

import com.enterprise.iam.authorization.application.service.AuthorizationCheckUseCase;
import com.enterprise.iam.authorization.application.service.ExplainDecisionUseCase;
import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;
import com.enterprise.iam.authorization.interfaces.http.dto.ApiResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.AuthorizationCheckRequest;
import com.enterprise.iam.authorization.interfaces.http.dto.AuthorizationCheckResponse;
import com.enterprise.iam.authorization.interfaces.http.dto.BatchAuthorizationCheckRequest;
import com.enterprise.iam.authorization.interfaces.http.dto.BatchAuthorizationCheckResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/authorization")
@ConditionalOnProperty(prefix = "iam.authorization", name = "enabled", havingValue = "true")
public class AuthorizationCheckController {

    private final AuthorizationCheckUseCase authorizationCheckUseCase;
    private final ExplainDecisionUseCase explainDecisionUseCase;

    public AuthorizationCheckController(
            AuthorizationCheckUseCase authorizationCheckUseCase,
            ExplainDecisionUseCase explainDecisionUseCase) {
        this.authorizationCheckUseCase = authorizationCheckUseCase;
        this.explainDecisionUseCase = explainDecisionUseCase;
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<AuthorizationCheckResponse>> check(
            @Valid @RequestBody AuthorizationCheckRequest request) {
        String traceId = UUID.randomUUID().toString();
        AuthorizationResult result =
                authorizationCheckUseCase.check(toDomainRequest(request));
        AuthorizationCheckResponse response = AuthorizationCheckResponse.fromResult(result);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/batch-check")
    public ResponseEntity<ApiResponse<BatchAuthorizationCheckResponse>> batchCheck(
            @Valid @RequestBody BatchAuthorizationCheckRequest request) {
        String traceId = UUID.randomUUID().toString();
        List<AuthorizationCheckResponse> results = request.checks().stream()
                .map(this::toDomainRequest)
                .map(authorizationCheckUseCase::check)
                .map(AuthorizationCheckResponse::fromResult)
                .toList();
        return ResponseEntity.ok(
                ApiResponse.ok(new BatchAuthorizationCheckResponse(results), traceId));
    }

    private AuthorizationRequest toDomainRequest(AuthorizationCheckRequest request) {
        return new AuthorizationRequest(
                request.tenantId(),
                request.subjectId(),
                request.sessionId(),
                request.resourceId(),
                request.operationId(),
                request.resourceInstanceId(),
                request.permissionVersion(),
                request.requestId(),
                request.context());
    }

    @GetMapping("/decisions/{decisionId}/explain")
    public ResponseEntity<ApiResponse<ExplainDecisionUseCase.ExplainResponse>> explain(
            @PathVariable long decisionId) {
        String traceId = UUID.randomUUID().toString();
        Optional<ExplainDecisionUseCase.ExplainResponse> result = explainDecisionUseCase.explain(decisionId);
        return result
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r, traceId)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(false, "IAM_AUTHZ_DECISION_NOT_FOUND", null, traceId)));
    }
}
