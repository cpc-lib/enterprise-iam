package com.enterprise.iam.organization.interfaces.rest;

import com.enterprise.iam.organization.application.command.AddTeamMemberCommand;
import com.enterprise.iam.organization.application.command.AssignTeamRoleCommand;
import com.enterprise.iam.organization.application.command.CreateTeamCommand;
import com.enterprise.iam.organization.application.command.CreateTeamRoleCommand;
import com.enterprise.iam.organization.application.command.MoveTeamCommand;
import com.enterprise.iam.organization.application.command.RemoveTeamMemberCommand;
import com.enterprise.iam.organization.application.service.AddTeamMemberUseCase;
import com.enterprise.iam.organization.application.service.AssignTeamRoleUseCase;
import com.enterprise.iam.organization.application.service.CreateTeamRoleUseCase;
import com.enterprise.iam.organization.application.service.CreateTeamUseCase;
import com.enterprise.iam.organization.application.service.MoveTeamUseCase;
import com.enterprise.iam.organization.application.service.RemoveTeamMemberUseCase;
import com.enterprise.iam.organization.interfaces.rest.dto.ApiResponse;
import com.enterprise.iam.organization.interfaces.rest.dto.CreateTeamRequest;
import com.enterprise.iam.organization.interfaces.rest.dto.CreateTeamRoleRequest;
import com.enterprise.iam.organization.interfaces.rest.dto.EntityResponse;
import com.enterprise.iam.organization.interfaces.rest.dto.MoveTeamRequest;
import com.enterprise.iam.organization.interfaces.rest.dto.VersionCommand;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/v1/teams")
@ConditionalOnProperty(prefix = "iam.organization", name = "enabled", havingValue = "true")
public class TeamController {

    private final CreateTeamUseCase createTeamUseCase;
    private final MoveTeamUseCase moveTeamUseCase;
    private final AddTeamMemberUseCase addTeamMemberUseCase;
    private final RemoveTeamMemberUseCase removeTeamMemberUseCase;
    private final CreateTeamRoleUseCase createTeamRoleUseCase;
    private final AssignTeamRoleUseCase assignTeamRoleUseCase;

    public TeamController(
            CreateTeamUseCase createTeamUseCase,
            MoveTeamUseCase moveTeamUseCase,
            AddTeamMemberUseCase addTeamMemberUseCase,
            RemoveTeamMemberUseCase removeTeamMemberUseCase,
            CreateTeamRoleUseCase createTeamRoleUseCase,
            AssignTeamRoleUseCase assignTeamRoleUseCase) {
        this.createTeamUseCase = createTeamUseCase;
        this.moveTeamUseCase = moveTeamUseCase;
        this.addTeamMemberUseCase = addTeamMemberUseCase;
        this.removeTeamMemberUseCase = removeTeamMemberUseCase;
        this.createTeamRoleUseCase = createTeamRoleUseCase;
        this.assignTeamRoleUseCase = assignTeamRoleUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EntityResponse>> create(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @Valid @RequestBody CreateTeamRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new CreateTeamCommand(
                tenantId,
                request.organizationId(),
                request.parentTeamId(),
                request.teamCode(),
                request.teamName(),
                traceId);
        var result = createTeamUseCase.create(command);
        var response = new EntityResponse(result.teamId(), result.version(), result.status().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response, traceId));
    }

    @PostMapping("/{teamId}/move")
    public ResponseEntity<ApiResponse<EntityResponse>> move(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long teamId,
            @Valid @RequestBody MoveTeamRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new MoveTeamCommand(
                tenantId,
                teamId,
                request.newParentTeamId(),
                request.version(),
                traceId);
        var result = moveTeamUseCase.move(command);
        var response = new EntityResponse(result.teamId(), result.version(), result.status().name());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PutMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> addMember(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long teamId,
            @PathVariable long userId,
            @Valid @RequestBody VersionCommand request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new AddTeamMemberCommand(tenantId, teamId, userId, traceId);
        addTeamMemberUseCase.add(command);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long teamId,
            @PathVariable long userId,
            @RequestHeader("If-Match-Version") long version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new RemoveTeamMemberCommand(tenantId, teamId, userId, version, traceId);
        removeTeamMemberUseCase.remove(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{teamId}/roles")
    public ResponseEntity<ApiResponse<EntityResponse>> createRole(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long teamId,
            @Valid @RequestBody CreateTeamRoleRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new CreateTeamRoleCommand(
                tenantId, teamId, request.roleCode(), request.roleName(), request.description(), traceId);
        var result = createTeamRoleUseCase.create(command);
        var response = new EntityResponse(result.teamRoleId(), result.version(), result.status().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response, traceId));
    }

    @PutMapping("/{teamId}/members/{userId}/roles/{roleId}")
    public ResponseEntity<Void> assignRole(
            @RequestHeader("X-Tenant-Id") long tenantId,
            @PathVariable long teamId,
            @PathVariable long userId,
            @PathVariable long roleId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String traceId = UUID.randomUUID().toString();
        var command = new AssignTeamRoleCommand(tenantId, teamId, userId, roleId, traceId);
        assignTeamRoleUseCase.assign(command);
        return ResponseEntity.noContent().build();
    }
}
