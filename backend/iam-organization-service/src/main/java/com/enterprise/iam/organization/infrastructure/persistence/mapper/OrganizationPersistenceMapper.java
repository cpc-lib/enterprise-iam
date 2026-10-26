package com.enterprise.iam.organization.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OrganizationPersistenceMapper {

    int insertOrganization(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("orgCode") String orgCode,
            @Param("orgName") String orgName,
            @Param("parentId") Long parentId,
            @Param("materializedPath") String materializedPath,
            @Param("depth") int depth,
            @Param("status") String status,
            @Param("version") long version,
            @Param("now") Instant now);

    OrganizationRow findOrganizationById(
            @Param("tenantId") long tenantId,
            @Param("organizationId") long organizationId);

    OrganizationRow findOrganizationByCode(
            @Param("tenantId") long tenantId,
            @Param("orgCode") String orgCode);

    List<OrganizationRow> findAllOrganizations(
            @Param("tenantId") long tenantId);

    int updateOrganization(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("orgCode") String orgCode,
            @Param("orgName") String orgName,
            @Param("parentId") Long parentId,
            @Param("materializedPath") String materializedPath,
            @Param("depth") int depth,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int updateOrganizationStatus(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int softDeleteOrganization(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int countChildOrganizations(
            @Param("tenantId") long tenantId,
            @Param("parentId") long parentId);

    int countDepartmentMembers(
            @Param("tenantId") long tenantId,
            @Param("departmentId") long departmentId);

    List<DepartmentMemberRow> findDepartmentMembers(
            @Param("tenantId") long tenantId,
            @Param("departmentId") long departmentId);

    int insertPosition(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("positionCode") String positionCode,
            @Param("positionName") String positionName,
            @Param("organizationId") Long organizationId,
            @Param("sortOrder") int sortOrder,
            @Param("description") String description,
            @Param("status") String status,
            @Param("version") long version,
            @Param("now") Instant now);

    PositionRow findPositionById(
            @Param("tenantId") long tenantId,
            @Param("positionId") long positionId);

    PositionRow findPositionByCode(
            @Param("tenantId") long tenantId,
            @Param("positionCode") String positionCode);

    List<PositionRow> listPositions(
            @Param("tenantId") long tenantId,
            @Param("organizationId") Long organizationId,
            @Param("status") String status,
            @Param("keyword") String keyword);

    int updatePosition(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("positionCode") String positionCode,
            @Param("positionName") String positionName,
            @Param("organizationId") Long organizationId,
            @Param("sortOrder") int sortOrder,
            @Param("description") String description,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int updatePositionStatus(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int softDeletePosition(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now);

    int insertTeam(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("organizationId") long organizationId,
            @Param("parentTeamId") Long parentTeamId,
            @Param("teamCode") String teamCode,
            @Param("teamName") String teamName,
            @Param("materializedPath") String materializedPath,
            @Param("depth") int depth,
            @Param("status") String status,
            @Param("version") long version,
            @Param("now") Instant now);

    TeamRow findTeamById(
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId);

    TeamRow findTeamByCode(
            @Param("tenantId") long tenantId,
            @Param("teamCode") String teamCode);

    int moveTeam(
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("newParentTeamId") Long newParentTeamId,
            @Param("expectedVersion") long expectedVersion);

    int insertTeamMember(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("userId") long userId,
            @Param("joinedAt") Instant joinedAt,
            @Param("now") Instant now);

    TeamMemberRow findActiveTeamMember(
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("userId") long userId);

    int removeTeamMember(
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion);

    int revokeTeamMemberRoles(
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("userId") long userId);

    int insertTeamRole(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("roleCode") String roleCode,
            @Param("roleName") String roleName,
            @Param("description") String description,
            @Param("status") String status,
            @Param("now") Instant now);

    TeamRoleRow findTeamRoleById(
            @Param("tenantId") long tenantId,
            @Param("teamRoleId") long teamRoleId);

    TeamRoleRow findTeamRoleByCode(
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("roleCode") String roleCode);

    int insertTeamMemberRole(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("userId") long userId,
            @Param("teamRoleId") long teamRoleId,
            @Param("startTime") Instant startTime,
            @Param("now") Instant now);

    TeamMemberRoleRow findActiveTeamMemberRole(
            @Param("tenantId") long tenantId,
            @Param("teamId") long teamId,
            @Param("userId") long userId,
            @Param("teamRoleId") long teamRoleId);

    record OrganizationRow(
            long id,
            long tenantId,
            String orgCode,
            String orgName,
            Long parentId,
            String materializedPath,
            int depth,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    record PositionRow(
            long id,
            long tenantId,
            String positionCode,
            String positionName,
            Long organizationId,
            int sortOrder,
            String description,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    record DepartmentMemberRow(
            long userId,
            long tenantId,
            String username,
            String displayName,
            String email,
            String phone,
            String status,
            Long departmentId,
            Long positionId,
            String positionName,
            Instant joinedAt) {
    }

    record TeamRow(
            long id,
            long tenantId,
            long organizationId,
            Long parentTeamId,
            String teamCode,
            String teamName,
            String materializedPath,
            int depth,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    record TeamMemberRow(
            long id,
            long tenantId,
            long teamId,
            long userId,
            String membershipStatus,
            Instant joinedAt,
            Instant leftAt,
            long version) {
    }

    record TeamRoleRow(
            long id,
            long tenantId,
            long teamId,
            String roleCode,
            String roleName,
            String description,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    record TeamMemberRoleRow(
            long id,
            long tenantId,
            long teamId,
            long userId,
            long teamRoleId,
            String status,
            Instant startTime,
            Instant expireTime,
            long version) {
    }
}
