package com.enterprise.iam.organization.interfaces.rest;

import com.enterprise.iam.organization.application.command.CreateOrganizationCommand;
import com.enterprise.iam.organization.application.service.CreateOrganizationUseCase;
import com.enterprise.iam.organization.application.service.DisableOrganizationUseCase;
import com.enterprise.iam.organization.application.service.EnableOrganizationUseCase;
import com.enterprise.iam.organization.application.service.GetOrganizationDetailUseCase;
import com.enterprise.iam.organization.application.service.GetOrganizationTreeUseCase;
import com.enterprise.iam.organization.application.service.ListOrganizationMembersUseCase;
import com.enterprise.iam.organization.application.service.UpdateOrganizationUseCase;
import com.enterprise.iam.organization.domain.model.DepartmentMember;
import com.enterprise.iam.organization.domain.model.DepartmentNode;
import com.enterprise.iam.organization.domain.model.Organization;
import com.enterprise.iam.organization.interfaces.rest.dto.ApiResponse;
import com.enterprise.iam.organization.interfaces.rest.dto.CreateOrganizationRequest;
import com.enterprise.iam.organization.interfaces.rest.dto.DepartmentMemberDto;
import com.enterprise.iam.organization.interfaces.rest.dto.DepartmentNodeDto;
import com.enterprise.iam.organization.interfaces.rest.dto.EntityResponse;
import com.enterprise.iam.organization.interfaces.rest.dto.UpdateOrganizationRequest;
import com.enterprise.iam.organization.interfaces.rest.dto.VersionCommand;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/organizations")
@ConditionalOnProperty(prefix = "iam.organization", name = "enabled", havingValue = "true")
public class OrganizationController {

    private final CreateOrganizationUseCase createOrganizationUseCase;
    private final GetOrganizationTreeUseCase getOrganizationTreeUseCase;
    private final GetOrganizationDetailUseCase getOrganizationDetailUseCase;
    private final UpdateOrganizationUseCase updateOrganizationUseCase;
    private final DisableOrganizationUseCase disableOrganizationUseCase;
    private final EnableOrganizationUseCase enableOrganizationUseCase;
    private final ListOrganizationMembersUseCase listOrganizationMembersUseCase;

    public OrganizationController(
            CreateOrganizationUseCase createOrganizationUseCase,
            GetOrganizationTreeUseCase getOrganizationTreeUseCase,
            GetOrganizationDetailUseCase getOrganizationDetailUseCase,
            UpdateOrganizationUseCase updateOrganizationUseCase,
            DisableOrganizationUseCase disableOrganizationUseCase,
            EnableOrganizationUseCase enableOrganizationUseCase,
            ListOrganizationMembersUseCase listOrganizationMembersUseCase) {
        this.createOrganizationUseCase = Objects.requireNonNull(createOrganizationUseCase);
        this.getOrganizationTreeUseCase = Objects.requireNonNull(getOrganizationTreeUseCase);
        this.getOrganizationDetailUseCase = Objects.requireNonNull(getOrganizationDetailUseCase);
        this.updateOrganizationUseCase = Objects.requireNonNull(updateOrganizationUseCase);
        this.disableOrganizationUseCase = Objects.requireNonNull(disableOrganizationUseCase);
        this.enableOrganizationUseCase = Objects.requireNonNull(enableOrganizationUseCase);
        this.listOrganizationMembersUseCase = Objects.requireNonNull(listOrganizationMembersUseCase);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EntityResponse>> create(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @Valid @RequestBody CreateOrganizationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new CreateOrganizationCommand(
                tenantId,
                request.orgCode(),
                request.orgName(),
                request.parentId(),
                traceId);
        var result = createOrganizationUseCase.create(command);
        var response = new EntityResponse(result.organizationId(), result.version(), result.status().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response, traceId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentNodeDto>>> listTree(
            @RequestHeader("X-Tenant-Id") long tenantId) {
        String traceId = UUID.randomUUID().toString();
        List<DepartmentNode> tree = getOrganizationTreeUseCase.getTree(tenantId);
        List<DepartmentNodeDto> dtos = tree.stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<DepartmentNodeDto>>> getTree(
            @RequestHeader("X-Tenant-Id") long tenantId) {
        return listTree(tenantId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentNodeDto>> getDetail(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id) {
        String traceId = UUID.randomUUID().toString();
        return getOrganizationDetailUseCase.getDetail(tenantId, id)
                .map(node -> ResponseEntity.ok(ApiResponse.ok(toDto(node), traceId)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EntityResponse>> update(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id,
            @Valid @RequestBody UpdateOrganizationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        Organization updated = updateOrganizationUseCase.update(
                tenantId,
                id,
                request.orgCode(),
                request.orgName(),
                request.parentId(),
                request.version());
        var response = new EntityResponse(updated.id(), updated.version(), updated.status().name());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<EntityResponse>> disable(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id,
            @Valid @RequestBody VersionCommand request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        Organization updated = disableOrganizationUseCase.disable(tenantId, id, request.version());
        var response = new EntityResponse(updated.id(), updated.version(), updated.status().name());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<EntityResponse>> enable(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id,
            @Valid @RequestBody VersionCommand request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        Organization updated = enableOrganizationUseCase.enable(tenantId, id, request.version());
        var response = new EntityResponse(updated.id(), updated.version(), updated.status().name());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<DepartmentMemberDto>>> listMembers(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long id) {
        String traceId = UUID.randomUUID().toString();
        List<DepartmentMember> members = listOrganizationMembersUseCase.listMembers(tenantId, id);
        List<DepartmentMemberDto> dtos = members.stream()
                .map(m -> new DepartmentMemberDto(
                        m.userId(),
                        m.username(),
                        m.displayName(),
                        m.email(),
                        m.phone(),
                        m.status(),
                        m.departmentId(),
                        m.positionId(),
                        m.positionName(),
                        m.joinedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos, traceId));
    }

    private DepartmentNodeDto toDto(DepartmentNode node) {
        List<DepartmentNodeDto> childDtos = node.children() == null
                ? List.of()
                : node.children().stream().map(this::toDto).toList();
        return new DepartmentNodeDto(
                node.id(),
                node.orgCode(),
                node.orgName(),
                node.parentId(),
                node.materializedPath(),
                node.depth(),
                node.status().name(),
                node.version(),
                node.memberCount(),
                node.createdAt(),
                node.updatedAt(),
                childDtos);
    }
}
