package com.enterprise.iam.organization.infrastructure.persistence;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.domain.model.DepartmentMember;
import com.enterprise.iam.organization.domain.model.Organization;
import com.enterprise.iam.organization.domain.model.OrganizationStatus;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper.DepartmentMemberRow;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper.OrganizationRow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcOrganizationRepository implements OrganizationRepository {

    private final OrganizationPersistenceMapper mapper;

    public JdbcOrganizationRepository(OrganizationPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Organization save(Organization organization) {
        int updated = mapper.insertOrganization(
                organization.id(),
                organization.tenantId(),
                organization.orgCode(),
                organization.orgName(),
                organization.parentId(),
                organization.materializedPath(),
                organization.depth(),
                organization.status().name(),
                organization.version(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("organization insert did not affect exactly one row");
        }
        return organization;
    }

    @Override
    public Optional<Organization> findById(long tenantId, long organizationId) {
        return Optional.ofNullable(mapper.findOrganizationById(tenantId, organizationId))
                .map(JdbcOrganizationRepository::toDomain);
    }

    @Override
    public Optional<Organization> findByCode(long tenantId, String orgCode) {
        return Optional.ofNullable(mapper.findOrganizationByCode(tenantId, orgCode))
                .map(JdbcOrganizationRepository::toDomain);
    }

    @Override
    public List<Organization> findAll(long tenantId) {
        return mapper.findAllOrganizations(tenantId).stream()
                .map(JdbcOrganizationRepository::toDomain)
                .toList();
    }

    @Override
    public boolean update(
            long tenantId,
            long organizationId,
            String orgCode,
            String orgName,
            Long parentId,
            String materializedPath,
            int depth,
            long expectedVersion) {
        int updated = mapper.updateOrganization(
                organizationId,
                tenantId,
                orgCode,
                orgName,
                parentId,
                materializedPath,
                depth,
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    @Override
    public boolean updateStatus(long tenantId, long organizationId, OrganizationStatus status, long expectedVersion) {
        int updated = mapper.updateOrganizationStatus(
                organizationId,
                tenantId,
                status.name(),
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    @Override
    public boolean delete(long tenantId, long organizationId, long expectedVersion) {
        int updated = mapper.softDeleteOrganization(
                organizationId,
                tenantId,
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    @Override
    public List<DepartmentMember> findMembers(long tenantId, long organizationId) {
        return mapper.findDepartmentMembers(tenantId, organizationId).stream()
                .map(this::toMemberDomain)
                .toList();
    }

    @Override
    public int countChildren(long tenantId, long parentId) {
        return mapper.countChildOrganizations(tenantId, parentId);
    }

    @Override
    public int countMembers(long tenantId, long organizationId) {
        return mapper.countDepartmentMembers(tenantId, organizationId);
    }

    private DepartmentMember toMemberDomain(DepartmentMemberRow row) {
        return new DepartmentMember(
                row.userId(),
                row.tenantId(),
                row.username(),
                row.displayName(),
                row.email(),
                row.phone(),
                row.status(),
                row.departmentId(),
                row.positionId(),
                row.positionName(),
                row.joinedAt());
    }

    private static Organization toDomain(OrganizationRow row) {
        return new Organization(
                row.id(),
                row.tenantId(),
                row.orgCode(),
                row.orgName(),
                row.parentId(),
                row.materializedPath(),
                row.depth(),
                OrganizationStatus.valueOf(row.status()),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }
}
