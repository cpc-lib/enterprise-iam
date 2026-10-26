package com.enterprise.iam.sharing.application.port.out;

import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;

import java.time.Instant;
import java.util.List;

/**
 * Appends share ACL domain events to the transactional outbox.
 * Implementations must participate in the caller's active business
 * transaction; the event commits or rolls back atomically with the mutation.
 *
 * <p>Both {@code appendGranted} and {@code appendRevoked} carry the next
 * monotonic {@code epoch} from {@link ShareProjectionEpochStore} so the
 * authorization-service projection fence mirrors exactly.
 */
public interface ShareAclEventAppender {

    void appendGranted(Share share, List<ShareAcl> acls, long epoch, Instant occurredAt);

    void appendRevoked(Share share, List<Long> operationIds, long epoch, Instant occurredAt);
}
