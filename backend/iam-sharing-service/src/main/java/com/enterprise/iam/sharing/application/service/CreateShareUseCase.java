package com.enterprise.iam.sharing.application.service;

import com.enterprise.iam.sharing.application.command.CreateShareCommand;
import com.enterprise.iam.sharing.application.exception.ShareDomainInvariantException;
import com.enterprise.iam.sharing.application.model.CreateShareResult;
import com.enterprise.iam.sharing.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.sharing.application.port.out.ShareAclEventAppender;
import com.enterprise.iam.sharing.application.port.out.ShareAclRepository;
import com.enterprise.iam.sharing.application.port.out.ShareProjectionEpochStore;
import com.enterprise.iam.sharing.application.port.out.ShareRepository;
import com.enterprise.iam.sharing.domain.model.Share;
import com.enterprise.iam.sharing.domain.model.ShareAcl;
import com.enterprise.iam.sharing.domain.model.ShareAclStatus;
import com.enterprise.iam.sharing.domain.model.ShareScopeType;
import com.enterprise.iam.sharing.domain.model.ShareStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CreateShareUseCase {

    private final ShareRepository shareRepository;
    private final ShareAclRepository shareAclRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;
    private final ShareAclEventAppender eventAppender;
    private final ShareProjectionEpochStore epochStore;

    public CreateShareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this(shareRepository, shareAclRepository, idGenerator, clock, null, null);
    }

    public CreateShareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository,
            PositiveIdGenerator idGenerator,
            Clock clock,
            ShareAclEventAppender eventAppender) {
        this(shareRepository, shareAclRepository, idGenerator, clock, eventAppender, null);
    }

    public CreateShareUseCase(
            ShareRepository shareRepository,
            ShareAclRepository shareAclRepository,
            PositiveIdGenerator idGenerator,
            Clock clock,
            ShareAclEventAppender eventAppender,
            ShareProjectionEpochStore epochStore) {
        this.shareRepository = Objects.requireNonNull(shareRepository);
        this.shareAclRepository = Objects.requireNonNull(shareAclRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
        this.eventAppender = eventAppender;
        this.epochStore = epochStore;
    }

    public CreateShareResult create(CreateShareCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Instant now = clock.instant();
        if (!command.expireAt().isAfter(now)) {
            throw new ShareDomainInvariantException("expireAt must be in the future");
        }
        if (new HashSet<>(command.operationIds()).size() != command.operationIds().size()) {
            throw new ShareDomainInvariantException("operationIds must be unique");
        }
        if (command.scopeType() == ShareScopeType.FOLDER_INHERITED
                && (command.folderPath() == null || command.folderPath().isBlank())) {
            throw new ShareDomainInvariantException("folderPath is required for FOLDER_INHERITED share");
        }
        if (command.scopeType() == ShareScopeType.COLLECTION
                && (command.collectionResourceIds() == null || command.collectionResourceIds().isEmpty())) {
            throw new ShareDomainInvariantException("collectionResourceIds is required for COLLECTION share");
        }
        Map<Long, List<ShareAcl.ShareFieldPermission>> fieldsByOperation =
                resolveFieldGrants(command.fields(), new HashSet<>(command.operationIds()));

        long shareId = idGenerator.nextId();
        long rootShareId = shareId;
        int shareDepth = command.parentShareId() == null ? 0 : 1;

        Share share = new Share(
                shareId,
                command.tenantId(),
                command.resourceId(),
                command.resourceInstanceKey(),
                command.targetType(),
                command.targetId(),
                command.creatorUserId(),
                command.parentShareId(),
                rootShareId,
                shareDepth,
                ShareStatus.ACTIVE,
                now,
                command.expireAt(),
                command.canReshare(),
                1,
                null,
                null,
                null,
                0,
                now,
                now,
                command.scopeType(),
                command.folderPath(),
                command.collectionResourceIds());
        shareRepository.save(share);

        List<ShareAcl> acls = new ArrayList<>();
        for (Long operationId : command.operationIds()) {
            long aclId = idGenerator.nextId();
            acls.add(new ShareAcl(
                    aclId,
                    command.tenantId(),
                    shareId,
                    operationId,
                    ShareAclStatus.ACTIVE,
                    0,
                    now,
                    fieldsByOperation.getOrDefault(operationId, List.of())));
        }
        shareAclRepository.saveAll(acls);

        if (eventAppender != null) {
            long epoch = epochStore != null
                    ? epochStore.nextEpoch(command.tenantId(), command.resourceId())
                    : 1L;
            eventAppender.appendGranted(share, acls, epoch, now);
        }

        return new CreateShareResult(
                shareId,
                ShareStatus.ACTIVE,
                0,
                share.scopeType(),
                share.folderPath(),
                share.collectionResourceIds());
    }

    /**
     * Validates the request field grants and groups them by operation.
     * Field grants are persisted and carried in the share ACL event
     * for authorization-service projection and runtime enforcement.
     */
    static Map<Long, List<ShareAcl.ShareFieldPermission>> resolveFieldGrants(
            List<CreateShareCommand.FieldGrant> fields, Set<Long> operationIds) {
        Map<Long, List<ShareAcl.ShareFieldPermission>> byOperation = new LinkedHashMap<>();
        Set<String> uniqueGrants = new HashSet<>();
        for (CreateShareCommand.FieldGrant grant : fields) {
            if (!operationIds.contains(grant.operationId())) {
                throw new ShareDomainInvariantException(
                        "field grant operationId must be one of the share operations");
            }
            if (grant.hidden() && (grant.readable() || grant.writable())) {
                throw new ShareDomainInvariantException(
                        "hidden field grant cannot be readable or writable");
            }
            String uniquenessKey = grant.operationId() + ":" + grant.fieldId();
            if (!uniqueGrants.add(uniquenessKey)) {
                throw new ShareDomainInvariantException(
                        "duplicate field grant for operationId and fieldId");
            }
            byOperation.computeIfAbsent(grant.operationId(), ignored -> new ArrayList<>())
                    .add(new ShareAcl.ShareFieldPermission(
                            grant.fieldId(),
                            grant.readable(),
                            grant.writable(),
                            grant.hidden(),
                            grant.maskStrategyId()));
        }
        return byOperation;
    }
}
