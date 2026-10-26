package com.enterprise.iam.sharing.interfaces.rest;

import com.enterprise.iam.sharing.application.command.CreateShareCommand;
import com.enterprise.iam.sharing.application.command.RevokeShareCommand;
import com.enterprise.iam.sharing.application.command.ReshareCommand;
import com.enterprise.iam.sharing.application.service.CreateShareUseCase;
import com.enterprise.iam.sharing.application.service.GetShareUseCase;
import com.enterprise.iam.sharing.application.service.ReshareUseCase;
import com.enterprise.iam.sharing.application.service.RevokeShareUseCase;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.interfaces.rest.dto.ApiResponse;
import com.enterprise.iam.sharing.interfaces.rest.dto.CreateShareRequest;
import com.enterprise.iam.sharing.interfaces.rest.dto.RevokeShareRequest;
import com.enterprise.iam.sharing.interfaces.rest.dto.ReshareRequest;
import com.enterprise.iam.sharing.interfaces.rest.dto.ShareResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shares")
@ConditionalOnProperty(prefix = "iam.sharing", name = "enabled", havingValue = "true")
public class ShareController {

    private final CreateShareUseCase createShareUseCase;
    private final RevokeShareUseCase revokeShareUseCase;
    private final ReshareUseCase reshareUseCase;
    private final GetShareUseCase getShareUseCase;

    public ShareController(
            CreateShareUseCase createShareUseCase,
            RevokeShareUseCase revokeShareUseCase,
            ReshareUseCase reshareUseCase,
            GetShareUseCase getShareUseCase) {
        this.createShareUseCase = createShareUseCase;
        this.revokeShareUseCase = revokeShareUseCase;
        this.reshareUseCase = reshareUseCase;
        this.getShareUseCase = getShareUseCase;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<ShareResponse>> create(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @RequestHeader("X-User-Id") long creatorUserId,
            @Valid @RequestBody CreateShareRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new CreateShareCommand(
                tenantId,
                request.resourceId(),
                request.resourceInstanceKey(),
                request.target().type(),
                request.target().id(),
                creatorUserId,
                null,
                request.canReshare(),
                request.expireAt(),
                request.operationIds(),
                toCommandFields(request.fields()),
                traceId,
                request.scopeType(),
                request.folderPath(),
                request.collectionResourceIds());
        var result = createShareUseCase.create(command);
        var response = new ShareResponse(
                result.shareId(),
                result.status(),
                result.version(),
                result.version() + 1,
                result.scopeType(),
                result.folderPath(),
                result.collectionResourceIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response, traceId));
    }

    @PostMapping("/{shareId}/revoke")
    @Transactional
    public ResponseEntity<ApiResponse<ShareResponse>> revoke(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long shareId,
            @Valid @RequestBody RevokeShareRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new RevokeShareCommand(
                tenantId, shareId, request.reason(), request.version(), traceId);
        var result = revokeShareUseCase.revoke(command);
        var response = new ShareResponse(
                result.shareId(),
                result.status(),
                result.version(),
                result.version() + 1,
                result.scopeType(),
                result.folderPath(),
                result.collectionResourceIds());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/{shareId}/reshare")
    @Transactional
    public ResponseEntity<ApiResponse<ShareResponse>> reshare(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @RequestHeader("X-User-Id") long creatorUserId,
            @PathVariable long shareId,
            @Valid @RequestBody ReshareRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var createCommand = new CreateShareCommand(
                tenantId,
                request.resourceId(),
                request.resourceInstanceKey(),
                request.target().type(),
                request.target().id(),
                creatorUserId,
                shareId,
                request.canReshare(),
                request.expireAt(),
                request.operationIds(),
                toCommandFields(request.fields()),
                traceId,
                request.scopeType(),
                request.folderPath(),
                request.collectionResourceIds());
        var result = reshareUseCase.reshare(new ReshareCommand(createCommand, request.parentVersion()));
        var response = new ShareResponse(
                result.shareId(),
                result.status(),
                result.version(),
                result.version() + 1,
                result.scopeType(),
                result.folderPath(),
                result.collectionResourceIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response, traceId));
    }

    @GetMapping("/{shareId}")
    public ResponseEntity<ApiResponse<ShareResponse>> get(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long shareId) {
        String traceId = UUID.randomUUID().toString();
        Share share = getShareUseCase.getShare(tenantId, shareId);
        var response = new ShareResponse(
                share.id(),
                share.status(),
                share.version(),
                share.version() + 1,
                share.scopeType(),
                share.folderPath(),
                share.collectionResourceIds());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    private static List<CreateShareCommand.FieldGrant> toCommandFields(
            List<CreateShareRequest.FieldGrant> fields) {
        return fields.stream()
                .map(field -> new CreateShareCommand.FieldGrant(
                        field.operationId(),
                        field.fieldId(),
                        field.readable(),
                        field.writable(),
                        field.hidden(),
                        field.maskStrategyId()))
                .toList();
    }
}
