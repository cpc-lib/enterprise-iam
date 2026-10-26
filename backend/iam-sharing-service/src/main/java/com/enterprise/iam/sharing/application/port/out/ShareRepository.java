package com.enterprise.iam.sharing.application.port.out;

import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareStatus;

import java.util.Optional;

public interface ShareRepository {

    Share save(Share share);

    Optional<Share> findById(long tenantId, long shareId);

    void revoke(long tenantId, long shareId, ShareStatus status, String reason, long expectedVersion);
}
