package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.ShareEpoch;
import com.enterprise.iam.authorization.domain.model.ShareGrant;

import java.time.Instant;
import java.util.List;

/**
 * Read side of the share ACL projection maintained from MQ events.
 */
public interface ShareAclProjectionRepository {

    List<ShareGrant> findActiveGrants(
            long tenantId,
            long subjectUserId,
            List<Long> teamIds,
            long resourceId,
            String resourceInstanceKey,
            long operationId,
            Instant evaluatedAt);

    ShareEpoch readEpoch(long tenantId, long resourceId);
}
