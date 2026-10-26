package com.enterprise.iam.identity.infrastructure.persistence.mapper;

import com.enterprise.iam.identity.domain.model.Role;
import com.enterprise.iam.identity.domain.model.Tenant;
import com.enterprise.iam.identity.domain.model.User;
import com.enterprise.iam.identity.domain.model.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface IdentityPersistenceMapper {

    int insertTenant(
            @Param("id") long id,
            @Param("tenantCode") String tenantCode,
            @Param("tenantName") String tenantName,
            @Param("status") String status,
            @Param("version") long version,
            @Param("now") Instant now);

    Tenant selectTenantById(@Param("tenantId") long tenantId);

    Tenant selectTenantByCode(@Param("tenantCode") String tenantCode);

    List<Tenant> selectTenants(
            @Param("keyword") String keyword,
            @Param("status") String status);

    int updateTenantStatus(
            @Param("tenantId") long tenantId,
            @Param("status") String status,
            @Param("expectedVersion") Long expectedVersion,
            @Param("now") Instant now);

    int updateTenantName(
            @Param("tenantId") long tenantId,
            @Param("tenantName") String tenantName,
            @Param("expectedVersion") Long expectedVersion,
            @Param("now") Instant now);

    int insertUser(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("username") String username,
            @Param("displayName") String displayName,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("departmentId") Long departmentId,
            @Param("positionId") Long positionId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("now") Instant now);

    User selectUserById(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId);

    Long selectTenantIdByUserId(@Param("userId") long userId);

    List<User> listUsers(
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword,
            @Param("departmentId") Long departmentId,
            @Param("positionId") Long positionId,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countUsers(
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword,
            @Param("departmentId") Long departmentId,
            @Param("positionId") Long positionId,
            @Param("status") String status);

    int updateUser(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("displayName") String displayName,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("departmentId") Long departmentId,
            @Param("positionId") Long positionId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int updateUserStatus(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int revokeAllUserSessions(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("now") Instant now);

    int revokeAllUserRefreshTokens(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("now") Instant now);

    int countTenantAdminRolesByUser(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId);

    int revokeAllTenantSessions(
            @Param("tenantId") long tenantId,
            @Param("now") Instant now);

    int revokeAllTenantRefreshTokens(
            @Param("tenantId") long tenantId,
            @Param("now") Instant now);

    Long selectMinUserIdByTenantId(@Param("tenantId") long tenantId);

    int upsertPasswordCredential(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("passwordPhc") String passwordPhc,
            @Param("now") Instant now);

    int upsertUserSecurityState(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("now") Instant now);

    int insertUserIdentity(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("identityType") String identityType,
            @Param("identityKey") String identityKey,
            @Param("credentialRef") Long credentialRef,
            @Param("status") String status,
            @Param("now") Instant now);

    int insertRole(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("roleCode") String roleCode,
            @Param("roleName") String roleName,
            @Param("description") String description,
            @Param("priority") int priority,
            @Param("status") String status,
            @Param("version") long version,
            @Param("now") Instant now);

    Role selectRoleById(
            @Param("tenantId") long tenantId,
            @Param("roleId") long roleId);

    Role selectRoleByCode(
            @Param("tenantId") long tenantId,
            @Param("roleCode") String roleCode);

    List<Role> selectRoles(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId);

    List<Role> selectRolesByUserId(
            @Param("tenantId") Long tenantId,
            @Param("userId") long userId);

    List<Long> selectRoleMenuIds(
            @Param("roleId") long roleId);

    int deleteRoleMenus(
            @Param("roleId") long roleId);

    Long selectTenantIdByRoleId(@Param("roleId") long roleId);

    int updateRoleStatus(
            @Param("tenantId") long tenantId,
            @Param("roleId") long roleId,
            @Param("status") com.enterprise.iam.identity.domain.model.RoleStatus status,
            @Param("expectedVersion") Long expectedVersion,
            @Param("now") Instant now);

    int softDeleteRole(
            @Param("tenantId") long tenantId,
            @Param("roleId") long roleId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("now") Instant now);

    int revokeUserRolesByRoleId(
            @Param("roleId") long roleId,
            @Param("now") Instant now);

    int insertRoleMenu(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("roleId") long roleId,
            @Param("menuId") long menuId,
            @Param("now") Instant now);

    int insertUserRole(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("roleId") long roleId,
            @Param("startTime") Instant startTime,
            @Param("expireTime") Instant expireTime,
            @Param("now") Instant now);

    UserRole selectActiveUserRole(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("roleId") long roleId);

    List<UserRole> selectActiveUserRolesByUser(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId);

    List<User> selectUsersByRoleId(
            @Param("tenantId") Long tenantId,
            @Param("roleId") long roleId);

    int revokeUserRole(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("roleId") long roleId,
            @Param("now") Instant now);

    int insertSodPolicy(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("policyCode") String policyCode,
            @Param("policyName") String policyName,
            @Param("description") String description,
            @Param("conflictingRoleIdsJson") String conflictingRoleIdsJson,
            @Param("maxAllowedRoles") int maxAllowedRoles,
            @Param("enforcementMode") String enforcementMode,
            @Param("severity") String severity,
            @Param("status") String status,
            @Param("version") long version,
            @Param("now") Instant now);

    List<SodPolicyRow> selectSodPoliciesByTenant(@Param("tenantId") long tenantId);

    SodPolicyRow selectSodPolicyById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    int updateSodPolicyStatus(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("status") String status,
            @Param("now") Instant now);

    int insertPermissionApprovalRequest(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("applicantUserId") long applicantUserId,
            @Param("requestType") String requestType,
            @Param("targetId") long targetId,
            @Param("targetName") String targetName,
            @Param("justification") String justification,
            @Param("durationHours") int durationHours,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    PermissionApprovalRow selectPermissionApprovalById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    List<PermissionApprovalRow> selectPermissionApprovalsByTenant(
            @Param("tenantId") long tenantId,
            @Param("status") String status);

    int updatePermissionApprovalDecision(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("status") String status,
            @Param("approverUserId") long approverUserId,
            @Param("decisionComment") String decisionComment,
            @Param("decidedAt") Instant decidedAt);

    Long findTeamIdByTeamRole(
            @Param("tenantId") long tenantId,
            @Param("teamRoleId") long teamRoleId);

    int insertTeamMemberRoleApproval(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("userId") long userId,
            @Param("teamRoleId") long teamRoleId,
            @Param("startTime") Instant startTime,
            @Param("expireTime") Instant expireTime,
            @Param("now") Instant now);

    int insertShareAclApproval(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("shareId") long shareId,
            @Param("targetType") String targetType,
            @Param("targetId") long targetId,
            @Param("permission") String permission,
            @Param("status") String status,
            @Param("startTime") Instant startTime,
            @Param("expireTime") Instant expireTime,
            @Param("now") Instant now);

    int insertMenu(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("parentId") Long parentId,
            @Param("menuKey") String menuKey,
            @Param("title") String title,
            @Param("path") String path,
            @Param("icon") String icon,
            @Param("sortOrder") int sortOrder,
            @Param("status") String status,
            @Param("publishStatus") String publishStatus,
            @Param("permissionCode") String permissionCode,
            @Param("component") String component,
            @Param("visible") boolean visible,
            @Param("version") long version,
            @Param("now") Instant now);

    MenuRow selectMenuById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    MenuRow selectMenuByKey(
            @Param("tenantId") long tenantId,
            @Param("menuKey") String menuKey);

    List<MenuRow> selectAllMenus(
            @Param("tenantId") long tenantId);

    List<MenuRow> selectPublishedActiveMenus(
            @Param("tenantId") long tenantId);

    List<MenuRow> selectPublishedMenusByUserRoles(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId);

    int updateMenu(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("parentId") Long parentId,
            @Param("menuKey") String menuKey,
            @Param("title") String title,
            @Param("path") String path,
            @Param("icon") String icon,
            @Param("sortOrder") int sortOrder,
            @Param("permissionCode") String permissionCode,
            @Param("component") String component,
            @Param("visible") boolean visible,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int updateMenuStatus(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int updateMenuPublishStatus(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("publishStatus") String publishStatus,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int publishAllDraftMenus(
            @Param("tenantId") long tenantId,
            @Param("now") Instant now);

    int updateMenuSortOrder(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("sortOrder") int sortOrder,
            @Param("now") Instant now);

    int softDeleteMenu(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int insertDictType(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("dictType") String dictType,
            @Param("dictName") String dictName,
            @Param("status") String status,
            @Param("description") String description,
            @Param("version") long version,
            @Param("now") Instant now);

    int updateDictType(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("dictName") String dictName,
            @Param("status") String status,
            @Param("description") String description,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int softDeleteDictType(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("now") Instant now);

    DictTypeRow selectDictTypeById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    DictTypeRow selectDictTypeByType(
            @Param("tenantId") long tenantId,
            @Param("dictType") String dictType);

    List<DictTypeRow> selectDictTypesPage(
            @Param("tenantId") long tenantId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countDictTypes(
            @Param("tenantId") long tenantId,
            @Param("keyword") String keyword,
            @Param("status") String status);

    int insertDictData(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("dictType") String dictType,
            @Param("dictLabel") String dictLabel,
            @Param("dictValue") String dictValue,
            @Param("sortOrder") int sortOrder,
            @Param("status") String status,
            @Param("cssTag") String cssTag,
            @Param("isDefault") boolean isDefault,
            @Param("description") String description,
            @Param("version") long version,
            @Param("now") Instant now);

    int updateDictData(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("dictLabel") String dictLabel,
            @Param("dictValue") String dictValue,
            @Param("sortOrder") int sortOrder,
            @Param("status") String status,
            @Param("cssTag") String cssTag,
            @Param("isDefault") boolean isDefault,
            @Param("description") String description,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int softDeleteDictData(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("now") Instant now);

    DictDataRow selectDictDataById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    List<DictDataRow> selectDictDataByType(
            @Param("tenantId") long tenantId,
            @Param("dictType") String dictType);

    List<DictDataRow> selectDictDataPage(
            @Param("tenantId") long tenantId,
            @Param("dictType") String dictType,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countDictData(
            @Param("tenantId") long tenantId,
            @Param("dictType") String dictType,
            @Param("keyword") String keyword,
            @Param("status") String status);

    record SodPolicyRow(
            long id,
            long tenantId,
            String policyCode,
            String policyName,
            String description,
            String conflictingRoleIdsJson,
            int maxAllowedRoles,
            String enforcementMode,
            String severity,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    record PermissionApprovalRow(
            long id,
            long tenantId,
            long applicantUserId,
            String requestType,
            long targetId,
            String targetName,
            String justification,
            int durationHours,
            String status,
            Long approverUserId,
            String decisionComment,
            Instant decidedAt,
            long version,
            Instant createdAt) {
    }

    record MenuRow(
            long id,
            long tenantId,
            Long parentId,
            String menuKey,
            String title,
            String path,
            String icon,
            int sortOrder,
            String status,
            String publishStatus,
            String permissionCode,
            String component,
            boolean visible,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    record DictTypeRow(
            long id,
            long tenantId,
            String dictType,
            String dictName,
            String status,
            String description,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    record DictDataRow(
            long id,
            long tenantId,
            String dictType,
            String dictLabel,
            String dictValue,
            int sortOrder,
            String status,
            String cssTag,
            boolean isDefault,
            String description,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }
}
