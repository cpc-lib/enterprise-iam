package com.enterprise.iam.audit.infrastructure.persistence.mapper;

import com.enterprise.iam.audit.domain.model.AuditBucketCount;
import com.enterprise.iam.audit.domain.model.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AuditLogPersistenceMapper {

    int insertAuditLog(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("actorId") long actorId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") long resourceId,
            @Param("detail") String detail,
            @Param("createdAt") Instant createdAt);

    int insertAuditLogIfSourceEventAbsent(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("actorId") long actorId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") long resourceId,
            @Param("detail") String detail,
            @Param("createdAt") Instant createdAt,
            @Param("sourceService") String sourceService,
            @Param("sourceEventId") long sourceEventId);

    Long selectIdBySourceEvent(
            @Param("sourceService") String sourceService,
            @Param("sourceEventId") long sourceEventId);

    List<AuditLog> selectByCriteria(
            @Param("tenantId") long tenantId,
            @Param("actorId") Long actorId,
            @Param("action") String action,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("limit") int limit);

    long countTotalByTenant(@Param("tenantId") long tenantId);

    List<AuditBucketCount> countGroupByAction(@Param("tenantId") long tenantId);

    List<AuditBucketCount> countGroupByResourceType(@Param("tenantId") long tenantId);
}
