package com.enterprise.iam.authorization.infrastructure.persistence.mapper;

import com.enterprise.iam.authorization.infrastructure.persistence.row.AbacPolicyRow;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface AuthorizationPersistenceMapper {

    int insertAbacPolicy(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("policyCode") String policyCode,
            @Param("policyName") String policyName,
            @Param("resourceType") String resourceType,
            @Param("operation") String operation,
            @Param("effect") String effect,
            @Param("priority") int priority,
            @Param("conditionExpr") String conditionExpr,
            @Param("description") String description,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);

    List<AbacPolicyRow> selectAbacPoliciesByTenant(
            @Param("tenantId") long tenantId);

    List<AbacPolicyRow> selectActiveAbacPoliciesByResourceAndOperation(
            @Param("tenantId") long tenantId,
            @Param("resourceType") String resourceType,
            @Param("operation") String operation);

    AbacPolicyRow selectAbacPolicyById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);

    int updateAbacPolicyStatus(
            @Param("tenantId") long tenantId,
            @Param("id") long id,
            @Param("status") String status,
            @Param("now") Instant now);

    int deleteAbacPolicyById(
            @Param("tenantId") long tenantId,
            @Param("id") long id);
}
