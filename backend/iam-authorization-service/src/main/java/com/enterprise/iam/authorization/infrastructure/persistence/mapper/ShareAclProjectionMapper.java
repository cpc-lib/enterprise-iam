package com.enterprise.iam.authorization.infrastructure.persistence.mapper;

import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareEpochRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareFieldGrantRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareFieldGrantWriteRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareGrantRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareProjectionWriteRow;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface ShareAclProjectionMapper {

    List<ShareGrantRow> selectActiveGrants(
            @Param("tenantId") long tenantId,
            @Param("subjectUserId") long subjectUserId,
            @Param("teamIds") List<Long> teamIds,
            @Param("resourceId") long resourceId,
            @Param("resourceInstanceKey") String resourceInstanceKey,
            @Param("operationId") long operationId,
            @Param("evaluatedAt") Instant evaluatedAt);

    List<ShareFieldGrantRow> selectFieldGrants(
            @Param("tenantId") long tenantId,
            @Param("shareIds") List<Long> shareIds,
            @Param("operationId") long operationId);

    ShareEpochRow selectEpoch(
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId);

    int upsertGranted(ShareProjectionWriteRow row);

    int revoke(
            @Param("epoch") long epoch,
            @Param("now") Instant now,
            @Param("tenantId") long tenantId,
            @Param("shareId") long shareId,
            @Param("targetType") String targetType,
            @Param("targetId") long targetId,
            @Param("operationIds") List<Long> operationIds);

    int deleteFieldGrantsBeforeEpoch(
            @Param("tenantId") long tenantId,
            @Param("shareId") long shareId,
            @Param("epoch") long epoch);

    int upsertFieldGrant(ShareFieldGrantWriteRow row);

    int advanceExpectedEpoch(
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId,
            @Param("epoch") long epoch,
            @Param("now") Instant now);

    int advanceAppliedEpoch(
            @Param("tenantId") long tenantId,
            @Param("resourceId") long resourceId,
            @Param("epoch") long epoch,
            @Param("now") Instant now);
}
