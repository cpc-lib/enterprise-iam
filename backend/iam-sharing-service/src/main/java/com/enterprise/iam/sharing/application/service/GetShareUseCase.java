package com.enterprise.iam.sharing.application.service;

import com.enterprise.iam.sharing.application.port.out.ShareAclRepository;
import com.enterprise.iam.sharing.application.port.out.ShareRepository;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;

import java.util.List;
import java.util.Objects;

public final class GetShareUseCase {

    private final ShareRepository shareRepository;
    private final ShareAclRepository shareAclRepository;

    public GetShareUseCase(ShareRepository shareRepository, ShareAclRepository shareAclRepository) {
        this.shareRepository = Objects.requireNonNull(shareRepository);
        this.shareAclRepository = Objects.requireNonNull(shareAclRepository);
    }

    public Share getShare(long tenantId, long shareId) {
        if (tenantId <= 0 || shareId <= 0) {
            throw new IllegalArgumentException("tenantId and shareId must be positive");
        }
        return shareRepository.findById(tenantId, shareId)
                .orElseThrow(() -> new IllegalArgumentException("share not found"));
    }

    public List<ShareAcl> getAcls(long tenantId, long shareId) {
        if (tenantId <= 0 || shareId <= 0) {
            throw new IllegalArgumentException("tenantId and shareId must be positive");
        }
        return shareAclRepository.findByShareId(tenantId, shareId);
    }
}
