package com.enterprise.iam.authorization.infrastructure.persistence.mapper;

import com.enterprise.iam.authorization.infrastructure.persistence.row.AuthorizationGrantRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.MaskStrategyRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ResolvedDataScopeRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ResolvedFieldPolicyRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ScopeTeamRow;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface AuthorizationPolicyMapper {

    Long selectTenantPermissionVersion(@Param("tenantId") long tenantId);

    Boolean selectResourceOperationEnabled(
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId,
            @Param("operationId") long operationId);

    List<AuthorizationGrantRow> selectRoleGrants(
            @Param("tenantId") long tenantId,
            @Param("subjectId") long subjectId,
            @Param("resourceId") long resourceId,
            @Param("operationId") long operationId);

    List<AuthorizationGrantRow> selectTeamRoleGrants(
            @Param("tenantId") long tenantId,
            @Param("subjectId") long subjectId,
            @Param("resourceId") long resourceId,
            @Param("operationId") long operationId);

    int insertDataScope(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("scopeCode") String scopeCode,
            @Param("scopeName") String scopeName,
            @Param("scopeType") String scopeType,
            @Param("customPolicyId") Long customPolicyId,
            @Param("now") Instant now);

    int insertFieldPolicy(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId,
            @Param("operationId") long operationId,
            @Param("fieldId") long fieldId,
            @Param("readable") boolean readable,
            @Param("writable") boolean writable,
            @Param("hidden") boolean hidden,
            @Param("maskStrategyId") Long maskStrategyId,
            @Param("priority") int priority,
            @Param("now") Instant now);

    int upsertTenantPermissionVersion(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("reason") String reason);

    int revokeRolePermissions(
            @Param("tenantId") long tenantId,
            @Param("roleId") long roleId,
            @Param("now") Instant now);

    int insertRolePermission(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("roleId") long roleId,
            @Param("permissionId") long permissionId,
            @Param("now") Instant now);

    List<Long> selectActiveRolePermissionIds(
            @Param("tenantId") long tenantId,
            @Param("roleId") long roleId);

    int revokeTeamRolePermissions(
            @Param("tenantId") long tenantId,
            @Param("teamRoleId") long teamRoleId,
            @Param("now") Instant now);

    Long selectTeamId(
            @Param("tenantId") long tenantId,
            @Param("teamRoleId") long teamRoleId);

    int insertTeamRolePermission(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("teamRoleId") long teamRoleId,
            @Param("permissionId") long permissionId,
            @Param("now") Instant now);

    List<Long> selectActiveTeamRolePermissionIds(
            @Param("tenantId") long tenantId,
            @Param("teamRoleId") long teamRoleId);

    int disableActiveResourceFields(
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId,
            @Param("now") Instant now);

    int insertResourceField(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId,
            @Param("fieldCode") String fieldCode,
            @Param("propertyPath") String propertyPath,
            @Param("columnName") String columnName,
            @Param("dataType") String dataType,
            @Param("sensitiveLevel") String sensitiveLevel,
            @Param("systemManaged") boolean systemManaged,
            @Param("now") Instant now);

    int deleteRoleDataScopeTeams(
            @Param("tenantId") long tenantId,
            @Param("rolePermissionId") long rolePermissionId);

    int deleteRoleDataScopes(
            @Param("tenantId") long tenantId,
            @Param("rolePermissionId") long rolePermissionId);

    int insertRoleDataScope(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("rolePermissionId") long rolePermissionId,
            @Param("dataScopeId") long dataScopeId,
            @Param("mergeMode") String mergeMode,
            @Param("now") Instant now);

    int insertRoleDataScopeTeam(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("roleDataScopeId") long roleDataScopeId,
            @Param("teamId") long teamId,
            @Param("includeChildren") boolean includeChildren,
            @Param("now") Instant now);

    int deleteRoleFieldPolicies(
            @Param("tenantId") long tenantId,
            @Param("rolePermissionId") long rolePermissionId);

    int insertRoleFieldPolicy(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("rolePermissionId") long rolePermissionId,
            @Param("fieldPolicyId") long fieldPolicyId,
            @Param("now") Instant now);

    List<ResolvedDataScopeRow> selectRoleDataScopes(
            @Param("tenantId") long tenantId,
            @Param("permissionIds") List<Long> permissionIds);

    List<ResolvedDataScopeRow> selectTeamRoleDataScopes(
            @Param("tenantId") long tenantId,
            @Param("permissionIds") List<Long> permissionIds);

    List<ScopeTeamRow> selectRoleDataScopeTeams(
            @Param("tenantId") long tenantId,
            @Param("bindingIds") List<Long> bindingIds);

    List<ScopeTeamRow> selectTeamRoleDataScopeTeams(
            @Param("tenantId") long tenantId,
            @Param("bindingIds") List<Long> bindingIds);

    List<ResolvedFieldPolicyRow> selectRoleFieldPolicies(
            @Param("tenantId") long tenantId,
            @Param("permissionIds") List<Long> permissionIds);

    List<ResolvedFieldPolicyRow> selectTeamRoleFieldPolicies(
            @Param("tenantId") long tenantId,
            @Param("permissionIds") List<Long> permissionIds);

    List<MaskStrategyRow> selectMaskStrategies(
            @Param("tenantId") long tenantId,
            @Param("strategyIds") List<Long> strategyIds);
}
