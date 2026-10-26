package com.enterprise.iam.sharing.application.service;

import com.enterprise.iam.sharing.application.command.RevokeShareCommand;
import com.enterprise.iam.sharing.application.model.CreateShareResult;
import com.enterprise.iam.sharing.application.port.out.ShareAclEventAppender;
import com.enterprise.iam.sharing.application.port.out.ShareAclRepository;
import com.enterprise.iam.sharing.application.port.out.ShareProjectionEpochStore;
import com.enterprise.iam.sharing.application.port.out.ShareRepository;
import com.enterprise.iam.sharing.application.port.out.ShareRevocationSecurityCoordinator;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;
import com.enterprise.iam.sharing.domain.model.ShareStatus;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class RevokeShareUseCase {

    private final ShareRepository shareRepository;
    private final ShareAclRepository shareAclRepository;
    private final ShareProjectionEpochStore epochStore;
    private final ShareAclEventAppender eventAppender;
    private final ShareRevocationSecurityCoordinator revocationSecurityCoordinator;
    private final Clock clock;

    public RevokeShareUseCase(ShareRepository shareRepository) {
        this(shareRepository, null, null, null, null, Clock.systemUTC());
    }

    public RevokeShareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository,
            ShareProjectionEpochStore epochStore,
            ShareAclEventAppender eventAppender,
            Clock clock) {
        this(shareRepository, shareAclRepository, epochStore, eventAppender, null, clock);
    }

    public RevokeShareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository,
            ShareProjectionEpochStore epochStore,
            ShareAclEventAppender eventAppender,
            ShareRevocationSecurityCoordinator revocationSecurityCoordinator,
            Clock clock) {
        this.shareRepository = Objects.requireNonNull(shareRepository);
        this.shareAclRepository = shareAclRepository;
        this.epochStore = epochStore;
        this.eventAppender = eventAppender;
        this.revocationSecurityCoordinator = revocationSecurityCoordinator;
        this.clock = Objects.requireNonNull(clock);
    }

    public CreateShareResult revoke(RevokeShareCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        shareRepository.revoke(
                command.tenantId(),
                command.shareId(),
                ShareStatus.REVOKED,
                command.reason(),
                command.version());
        Share share = shareRepository.findById(command.tenantId(), command.shareId())
                .orElseThrow(() -> new IllegalStateException("share not found after revoke"));

        if (shareAclRepository != null && epochStore != null) {
            List<Long> operationIds = shareAclRepository
                    .findByShareId(command.tenantId(), command.shareId())
                    .stream()
                    .map(ShareAcl::operationId)
                    .distinct()
                    .sorted()
                    .toList();
            long epoch = epochStore.nextEpoch(command.tenantId(), share.resourceId());
            if (eventAppender != null) {
                eventAppender.appendRevoked(share, operationIds, epoch, clock.instant());
            }
            if (revocationSecurityCoordinator != null) {
                revocationSecurityCoordinator.coordinateRevocation(command.tenantId(), share.resourceId(), epoch);
            }
        }

        return new CreateShareResult(share.id(), share.status(), share.version());
    }
}
