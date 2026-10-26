package com.enterprise.iam.organization.application.port.out;

import com.enterprise.iam.organization.domain.model.DepartmentMember;
import com.enterprise.iam.organization.domain.model.Organization;
import com.enterprise.iam.organization.domain.model.OrganizationStatus;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {

    Organization save(Organization organization);

    Optional<Organization> findById(long tenantId, long organizationId);

    Optional<Organization> findByCode(long tenantId, String orgCode);

    List<Organization> findAll(long tenantId);

    boolean update(
            long tenantId,
            long organizationId,
            String orgCode,
            String orgName,
            Long parentId,
            String materializedPath,
            int depth,
            long expectedVersion);

    boolean updateStatus(long tenantId, long organizationId, OrganizationStatus status, long expectedVersion);

    boolean delete(long tenantId, long organizationId, long expectedVersion);

    List<DepartmentMember> findMembers(long tenantId, long organizationId);

    int countChildren(long tenantId, long parentId);

    int countMembers(long tenantId, long organizationId);
}
