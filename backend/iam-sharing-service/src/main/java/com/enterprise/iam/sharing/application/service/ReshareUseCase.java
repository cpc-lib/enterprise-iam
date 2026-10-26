package com.enterprise.iam.sharing.application.service;

import com.enterprise.iam.sharing.application.command.CreateShareCommand;
import com.enterprise.iam.sharing.application.command.ReshareCommand;
import com.enterprise.iam.sharing.application.model.CreateShareResult;
import com.enterprise.iam.sharing.application.port.out.ShareAclRepository;
import com.enterprise.iam.sharing.application.port.out.ShareRepository;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;
import com.enterprise.iam.sharing.domain.model.ShareStatus;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ReshareUseCase {

    private final ShareRepository shareRepository;
    private final ShareAclRepository shareAclRepository;
    private final CreateShareUseCase createShareUseCase;
    private final Clock clock;

    public ReshareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository,
            CreateShareUseCase createShareUseCase,
            Clock clock) {
        this.shareRepository = Objects.requireNonNull(shareRepository);
        this.shareAclRepository = Objects.requireNonNull(shareAclRepository);
        this.createShareUseCase = Objects.requireNonNull(createShareUseCase);
        this.clock = Objects.requireNonNull(clock);
    }

    public CreateShareResult reshare(ReshareCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CreateShareCommand createCommand = command.command();
        long parentShareId = createCommand.parentShareId();

        Share parent = shareRepository.findById(createCommand.tenantId(), parentShareId)
                .orElseThrow(() -> new IllegalArgumentException("parent share not found"));
        if (!parent.canReshare()) {
            throw new IllegalStateException("parent share does not allow reshare");
        }
        if (parent.status() != ShareStatus.ACTIVE) {
            throw new IllegalStateException("parent share is not active");
        }
        if (!parent.expireTime().isAfter(clock.instant())) {
            throw new IllegalStateException("parent share is expired");
        }
        if (parent.version() != command.parentVersion()) {
            throw new IllegalStateException("parent version conflict");
        }

        List<ShareAcl> parentAcls = shareAclRepository.findByShareId(createCommand.tenantId(), parentShareId);
        Set<Long> parentOperations = new HashSet<>();
        for (ShareAcl acl : parentAcls) {
            parentOperations.add(acl.operationId());
        }
        List<Long> intersected = createCommand.operationIds().stream()
                .filter(parentOperations::contains)
                .distinct()
                .toList();
        if (intersected.isEmpty()) {
            throw new IllegalStateException("reshare has no overlapping operations");
        }

        // Field grants are persisted on the child only for operations that
        // survived the parent/right intersection.
        Set<Long> boundedOperations = new HashSet<>(intersected);
        List<CreateShareCommand.FieldGrant> boundedFields = createCommand.fields().stream()
                .filter(field -> boundedOperations.contains(field.operationId()))
                .toList();

        CreateShareCommand bounded = new CreateShareCommand(
                createCommand.tenantId(),
                createCommand.resourceId(),
                createCommand.resourceInstanceKey(),
                createCommand.targetType(),
                createCommand.targetId(),
                createCommand.creatorUserId(),
                parentShareId,
                createCommand.canReshare(),
                createCommand.expireAt(),
                intersected,
                boundedFields,
                createCommand.requestId(),
                createCommand.scopeType(),
                createCommand.folderPath(),
                createCommand.collectionResourceIds());
        return createShareUseCase.create(bounded);
    }
}
