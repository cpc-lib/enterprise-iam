package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.domain.model.DepartmentNode;
import com.enterprise.iam.organization.domain.model.Organization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GetOrganizationTreeUseCase {

    private final OrganizationRepository organizationRepository;

    public GetOrganizationTreeUseCase(OrganizationRepository organizationRepository) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
    }

    public List<DepartmentNode> getTree(long tenantId) {
        List<Organization> orgs = organizationRepository.findAll(tenantId);
        Map<Long, DepartmentNode> nodeMap = new LinkedHashMap<>();

        for (Organization org : orgs) {
            int memberCount = organizationRepository.countMembers(tenantId, org.id());
            DepartmentNode node = new DepartmentNode(
                    org.id(),
                    org.tenantId(),
                    org.orgCode(),
                    org.orgName(),
                    org.parentId(),
                    org.materializedPath(),
                    org.depth(),
                    org.status(),
                    org.version(),
                    memberCount,
                    org.createdAt(),
                    org.updatedAt());
            nodeMap.put(org.id(), node);
        }

        List<DepartmentNode> roots = new ArrayList<>();
        for (DepartmentNode node : nodeMap.values()) {
            if (node.parentId() == null || !nodeMap.containsKey(node.parentId())) {
                roots.add(node);
            } else {
                DepartmentNode parent = nodeMap.get(node.parentId());
                parent.addChild(node);
            }
        }
        return roots;
    }
}
