package com.enterprise.iam.auth.interfaces.http;

import com.enterprise.iam.auth.application.service.WebAuthnPasskeyUseCase;
import com.enterprise.iam.auth.domain.model.WebAuthnCredential;
import com.enterprise.iam.auth.interfaces.http.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/webauthn")
@ConditionalOnProperty(prefix = "iam.auth.http", name = "enabled", havingValue = "true")
public class WebAuthnController {

    private final WebAuthnPasskeyUseCase webAuthnPasskeyUseCase;

    public WebAuthnController(WebAuthnPasskeyUseCase webAuthnPasskeyUseCase) {
        this.webAuthnPasskeyUseCase = webAuthnPasskeyUseCase;
    }

    @PostMapping("/register/options")
    public ResponseEntity<ApiResponse<WebAuthnPasskeyUseCase.RegistrationOptions>> createRegistrationOptions(
            @Valid @RequestBody RegistrationOptionsRequest request) {
        String traceId = UUID.randomUUID().toString();
        WebAuthnPasskeyUseCase.RegistrationOptions options = webAuthnPasskeyUseCase.createRegistrationOptions(
                request.tenantId(),
                request.userId(),
                request.username(),
                request.rpId());
        return ResponseEntity.ok(ApiResponse.ok(options, traceId));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<CredentialSummaryDto>> verifyRegistration(
            @Valid @RequestBody RegistrationVerifyRequest request) {
        String traceId = UUID.randomUUID().toString();
        try {
            WebAuthnCredential credential = webAuthnPasskeyUseCase.verifyRegistration(
                    request.tenantId(),
                    request.userId(),
                    request.challengeId(),
                    request.credentialId(),
                    request.publicKeyCose(),
                    request.signCount() == null ? 0L : request.signCount(),
                    request.aaguid(),
                    request.transports(),
                    request.deviceName());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(CredentialSummaryDto.from(credential), traceId));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("IAM_WEBAUTHN_REGISTRATION_FAILED", traceId));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("IAM_WEBAUTHN_CREDENTIAL_EXISTS", traceId));
        }
    }

    @PostMapping("/login/options")
    public ResponseEntity<ApiResponse<WebAuthnPasskeyUseCase.AuthenticationOptions>> createAuthenticationOptions(
            @Valid @RequestBody AuthenticationOptionsRequest request) {
        String traceId = UUID.randomUUID().toString();
        WebAuthnPasskeyUseCase.AuthenticationOptions options = webAuthnPasskeyUseCase.createAuthenticationOptions(
                request.tenantId(),
                request.userId(),
                request.username(),
                request.rpId());
        return ResponseEntity.ok(ApiResponse.ok(options, traceId));
    }

    @PostMapping("/login/verify")
    public ResponseEntity<ApiResponse<WebAuthnPasskeyUseCase.AuthenticationVerifyResult>> verifyAuthentication(
            @Valid @RequestBody AuthenticationVerifyRequest request) {
        String traceId = UUID.randomUUID().toString();
        try {
            WebAuthnPasskeyUseCase.AuthenticationVerifyResult result = webAuthnPasskeyUseCase.verifyAuthentication(
                    request.tenantId(),
                    request.challengeId(),
                    request.credentialId(),
                    request.signCount() == null ? 0L : request.signCount(),
                    request.clientDataJsonB64(),
                    request.signatureB64());
            return ResponseEntity.ok(ApiResponse.ok(result, traceId));
        } catch (SecurityException ex) {
            String code = ex.getMessage() != null && ex.getMessage().contains("IAM_WEBAUTHN_CLONE_DETECTED")
                    ? "IAM_WEBAUTHN_CLONE_DETECTED"
                    : "IAM_WEBAUTHN_AUTHENTICATION_FAILED";
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(code, traceId));
        }
    }

    @GetMapping("/credentials")
    public ResponseEntity<ApiResponse<List<CredentialSummaryDto>>> listCredentials(
            @RequestParam Long tenantId,
            @RequestParam Long userId) {
        String traceId = UUID.randomUUID().toString();
        List<CredentialSummaryDto> list = webAuthnPasskeyUseCase.listCredentials(tenantId, userId).stream()
                .map(CredentialSummaryDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list, traceId));
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeCredential(
            @PathVariable Long id,
            @RequestParam Long tenantId,
            @RequestParam Long userId) {
        String traceId = UUID.randomUUID().toString();
        webAuthnPasskeyUseCase.revokeCredential(tenantId, userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    public record RegistrationOptionsRequest(
            @NotNull Long tenantId,
            @NotNull Long userId,
            @NotBlank String username,
            String rpId) {
    }

    public record RegistrationVerifyRequest(
            @NotNull Long tenantId,
            @NotNull Long userId,
            @NotBlank String challengeId,
            @NotBlank String credentialId,
            @NotBlank String publicKeyCose,
            Long signCount,
            String aaguid,
            String transports,
            String deviceName) {
    }

    public record AuthenticationOptionsRequest(
            @NotNull Long tenantId,
            Long userId,
            String username,
            String rpId) {
    }

    public record AuthenticationVerifyRequest(
            @NotNull Long tenantId,
            @NotBlank String challengeId,
            @NotBlank String credentialId,
            Long signCount,
            @NotBlank String clientDataJsonB64,
            @NotBlank String signatureB64) {
    }

    public record CredentialSummaryDto(
            String id,
            String tenantId,
            String userId,
            String credentialId,
            long signCount,
            String aaguid,
            String transports,
            String deviceName,
            String status,
            Instant createdAt,
            Instant lastUsedAt) {

        public static CredentialSummaryDto from(WebAuthnCredential c) {
            return new CredentialSummaryDto(
                    Long.toUnsignedString(c.id()),
                    Long.toUnsignedString(c.tenantId()),
                    Long.toUnsignedString(c.userId()),
                    c.credentialId(),
                    c.signCount(),
                    c.aaguid(),
                    c.transports(),
                    c.deviceName(),
                    c.status().name(),
                    c.createdAt(),
                    c.lastUsedAt());
        }
    }
}
